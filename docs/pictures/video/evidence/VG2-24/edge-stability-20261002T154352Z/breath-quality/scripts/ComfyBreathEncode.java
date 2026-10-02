import app.melotrail.video.adapter.*;
import app.melotrail.video.domain.*;
import kotlinx.serialization.json.*;
import java.nio.file.*;
import java.net.http.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** One graph submission through the existing owned ComfyUI runtime and protocol client. */
public final class ComfyBreathEncode {
    static void require(boolean ok, String text) { if (!ok) throw new IllegalStateException(text); }
    static String field(JsonObject object, String key) { return ((JsonPrimitive)object.get(key)).getContent(); }
    static String sha(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    static JsonObject read(Path path) throws Exception { return (JsonObject)Json.Default.parseToJsonElement(Files.readString(path)); }
    static String get(ComfyVideoConnection connection, String route) throws Exception {
        var reply=HttpClient.newHttpClient().send(HttpRequest.newBuilder(connection.httpUri(ComfyVideoHttpMethod.GET,route)).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());
        require(reply.statusCode()==200,"Read failed: "+route+" status="+reply.statusCode()); return reply.body();
    }
    public static void main(String[] args) throws Exception {
        require(args.length==1,"Supply this run's request");
        var request=read(Path.of(args[0])); var run=Path.of(field(request,"evidence"));
        var output=Files.createDirectory(run.resolve("output")); var graphPath=run.resolve("workflow/finish-full-frame-api.json");
        var graph=read(graphPath); var source=Path.of(field(request,"source"));
        require(sha(source).equals(field(request,"sourceSha256")),"Accepted source changed");
        var setup=LocalVideoSetup.Companion.bundled().inspect(Path.of(field(request,"applicationSupportRoot")));
        require(setup.getState()==LocalVideoSetupState.READY,"Pinned setup not ready: "+setup.getIssues());
        var runtime=new ComfyVideoRuntime(); var hook=new Thread(runtime::close,"owned-comfy-finisher-shutdown");
        Runtime.getRuntime().addShutdownHook(hook); ComfyVideoConnection connection=null;
        String prompt=field(request,"promptId"),clientId=field(request,"clientId");
        long started=System.nanoTime();
        try {
            var session=runtime.start(setup.getReady(),Path.of(field(request,"applicationSupportRoot")).resolve("runs"),8199);
            Files.writeString(output.resolve("session-path.txt"),session.getDirectory().toString(),StandardOpenOption.CREATE_NEW);
            System.out.println("SESSION="+session.getDirectory());
            Files.copy(source,session.getInputDirectory().resolve("prepared-frames.png"));
            require(sha(source).equals(sha(session.getInputDirectory().resolve("prepared-frames.png"))),"Input copy differs");
            connection=runtime.connection(session);
            var definitions=(JsonObject)Json.Default.parseToJsonElement(get(connection,"/object_info"));
            Map<String,JsonElement> selected=new TreeMap<>();
            for(var value:graph.values()) { String type=field((JsonObject)value,"class_type"); require(Set.of("LoadImage","CreateVideo","SaveVideo").contains(type) && definitions.containsKey(type),"Missing or unexpected node "+type); selected.put(type,definitions.get(type)); }
            Files.writeString(run.resolve("checks/node-definitions.json"),new JsonObject(selected).toString(),StandardOpenOption.CREATE_NEW);
            var binding=new VideoComfyOutputBinding("16",Set.of("mp4"));
            var api=new ComfyVideoClient(connection,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),Duration.ofSeconds(15),Duration.ofMillis(200));
            try(var slot=runtime.acquireInferenceSlot(session,prompt)) {
                require(slot.claimSubmission$melotrail(),"Single submission was already consumed");
                Files.writeString(run.resolve("checks/submission-once.txt"),prompt,StandardOpenOption.CREATE_NEW);
                var submission=new ComfyClientSubmission(prompt,clientId,graph,Map.of(),List.of(),binding,"vg2-24-comfy-fullhd",field(request,"fingerprint"),field(request,"attemptId"),field(request,"ownershipToken"));
                var submitted=api.submit(submission); System.out.println("SUBMISSION="+submitted);
                require(submitted instanceof ComfyClientSubmissionResult.Accepted || submitted instanceof ComfyClientSubmissionResult.Uncertain,"Graph submission rejected: "+submitted);
                int polls=0;
                while(true) {
                    require(Duration.ofNanos(System.nanoTime()-started).getSeconds()<180,"Bounded finisher timed out");
                    require(runtime.getState()==ComfyVideoRuntimeState.READY,"Owned runtime failed: "+runtime.getLastFailure());
                    var observed=api.observe(prompt,clientId,binding);
                    if(++polls%10==0)System.out.println("OBSERVATION="+observed);
                    if(observed instanceof ComfyClientObservation.Completed completed) {
                        var artifact=completed.getOutput(); var movie=session.getOutputDirectory().resolve(artifact.getSubfolder()).resolve(artifact.getFileName()).normalize();
                        require(movie.startsWith(session.getOutputDirectory())&&Files.isRegularFile(movie),"Unsafe or missing output");
                        var review=run.resolve("review/tabi-breath-comfy-1080p.mp4"); Files.copy(movie,review);
                        require(sha(review).equals(sha(movie)),"Review copy differs");
                        Files.writeString(run.resolve("checks/output-sha256.txt"),sha(review)+"\n",StandardOpenOption.CREATE_NEW);
                        System.out.println("COMFY_FINISH_COMPLETED="+review); break;
                    }
                    require(!(observed instanceof ComfyClientObservation.Failed)&&!(observed instanceof ComfyClientObservation.Cancelled),"Graph execution failed: "+observed);
                    Thread.sleep(2000);
                }
            }
        } finally {
            if(connection!=null)try {Files.writeString(run.resolve("checks/history.json"),get(connection,"/history/"+prompt),StandardOpenOption.CREATE_NEW);}catch(Exception historyFailure){System.out.println("HISTORY_READ_FAILURE="+historyFailure);}
            runtime.close(); Runtime.getRuntime().removeShutdownHook(hook);
            System.out.println("RUNTIME_FINAL="+runtime.getState());
            System.out.println("ELAPSED_SECONDS="+Duration.ofNanos(System.nanoTime()-started).toMillis()/1000.0);
            require(sha(source).equals(field(request,"sourceSha256")),"Accepted source changed");
        }
    }
}
