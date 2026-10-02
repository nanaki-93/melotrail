import app.melotrail.video.adapter.*;
import app.melotrail.video.domain.*;
import kotlinx.serialization.json.*;
import java.nio.file.*;
import java.net.http.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** One finite model-free ComfyUI image/mask batches; existing runtime/client own execution. */
public final class ComfyEdgeBatchPort {
    static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    static String field(JsonObject o,String k){return ((JsonPrimitive)o.get(k)).getContent();}
    static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    static JsonObject read(Path p)throws Exception{return (JsonObject)Json.Default.parseToJsonElement(Files.readString(p));}
    static String get(ComfyVideoConnection c,String route)throws Exception{
        var r=HttpClient.newHttpClient().send(HttpRequest.newBuilder(c.httpUri(ComfyVideoHttpMethod.GET,route)).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());
        require(r.statusCode()==200,"Read failed: "+route);return r.body();
    }
    public static void main(String[] args)throws Exception{
        var q=read(Path.of(args[0]));var run=Path.of(field(q,"evidence"));var output=Files.createDirectory(run.resolve("output"));
        var backing=Path.of(field(q,"backing"));require(sha(backing).equals(field(q,"backingSha256")),"Backing changed");
        var batches=(JsonArray)q.get("batches");require(batches.size()==1,"Only one admitted recovery batch");
        var setup=LocalVideoSetup.Companion.bundled().inspect(Path.of(field(q,"applicationSupportRoot")));
        require(setup.getState()==LocalVideoSetupState.READY,"Pinned runtime not ready");
        var runtime=new ComfyVideoRuntime();var hook=new Thread(runtime::close,"owned-mask-proof-shutdown");Runtime.getRuntime().addShutdownHook(hook);
        long start=System.nanoTime();
        try{
            var session=runtime.start(setup.getReady(),Path.of(field(q,"applicationSupportRoot")).resolve("runs"),Integer.parseInt(field(q,"port")));
            Files.writeString(output.resolve("session-path.txt"),session.getDirectory().toString(),StandardOpenOption.CREATE_NEW);
            Files.copy(backing,session.getInputDirectory().resolve("backing.png"));
            var c=runtime.connection(session);var api=new ComfyVideoClient(c,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),Duration.ofSeconds(15),Duration.ofMillis(200));
            var defs=(JsonObject)Json.Default.parseToJsonElement(get(c,"/object_info"));
            Map<String,JsonElement> selected=new TreeMap<>();
            int index=0;
            for(var item:batches){
                var b=(JsonObject)item;var source=Path.of(field(b,"source"));var graphPath=Path.of(field(b,"graph"));
                require(sha(source).equals(field(b,"sourceSha256"))&&sha(graphPath).equals(field(b,"graphSha256")),"Batch input changed");
                String filename="layer-"+index+".png";Files.copy(source,session.getInputDirectory().resolve(filename));
                var graph=read(graphPath);
                for(var node:graph.values()){
                    String type=field((JsonObject)node,"class_type");
                    require(Set.of("LoadImage","InvertMask","RepeatImageBatch","ImageCompositeMasked","SaveAnimatedPNG").contains(type)&&defs.containsKey(type),"Unexpected/missing node "+type);
                    selected.put(type,defs.get(type));
                }
                String prompt=field(b,"promptId"),client=field(b,"clientId");var binding=new VideoComfyOutputBinding("9",Set.of("png"));
                try(var slot=runtime.acquireInferenceSlot(session,prompt)){
                    require(slot.claimSubmission$melotrail(),"Duplicate submission");
                    Files.writeString(run.resolve("checks/submission-"+index+".txt"),prompt,StandardOpenOption.CREATE_NEW);
                    var submitted=api.submit(new ComfyClientSubmission(prompt,client,graph,Map.of(),List.of(),binding,"vg2-24-fixed-cabin",field(b,"fingerprint"),field(b,"attemptId"),field(b,"ownershipToken")));
                    require(submitted instanceof ComfyClientSubmissionResult.Accepted||submitted instanceof ComfyClientSubmissionResult.Uncertain,"Submission rejected: "+submitted);
                    while(true){
                        require(Duration.ofNanos(System.nanoTime()-start).getSeconds()<180,"Compositing deadline");
                        require(runtime.getState()==ComfyVideoRuntimeState.READY,"Runtime failed: "+runtime.getLastFailure());
                        var observed=api.observe(prompt,client,binding);
                        if(observed instanceof ComfyClientObservation.Completed done){
                            var a=done.getOutput();var p=session.getOutputDirectory().resolve(a.getSubfolder()).resolve(a.getFileName()).normalize();
                            require(p.startsWith(session.getOutputDirectory())&&Files.isRegularFile(p),"Unsafe output");
                            Files.copy(p,run.resolve("review/batch-"+index+".png"));System.out.println("BATCH_COMPLETED="+index+" sha256="+sha(p));break;
                        }
                        require(!(observed instanceof ComfyClientObservation.Failed)&&!(observed instanceof ComfyClientObservation.Cancelled),"Batch failed: "+observed);
                        Thread.sleep(500);
                    }
                }finally{Files.writeString(run.resolve("checks/history-"+index+".json"),get(c,"/history/"+prompt),StandardOpenOption.CREATE_NEW);}
                index++;
            }
            Files.writeString(run.resolve("checks/node-definitions.json"),new JsonObject(selected).toString(),StandardOpenOption.CREATE_NEW);
        }finally{runtime.close();Runtime.getRuntime().removeShutdownHook(hook);System.out.println("RUNTIME_FINAL="+runtime.getState());}
    }
}
