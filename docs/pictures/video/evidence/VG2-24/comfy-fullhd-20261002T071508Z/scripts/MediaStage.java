import app.melotrail.video.adapter.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
/** This proof uses the existing production process owner, not a second supervisor. */
public final class MediaStage {
 public static void main(String[] args) throws Exception {
  var env=new HashMap<String,String>(); env.put("LC_ALL","C");env.put("LANG","C");
  env.put("PYTHONNOUSERSITE","1");env.put("PYTHONPATH","/Users/marcoandreose/Library/Application Support/MelotrailVideo/tools/comfyui/v0.35.0/.venv/lib/python3.12/site-packages");
  env.put("VIRTUAL_ENV","/Users/marcoandreose/Library/Application Support/MelotrailVideo/tools/comfyui/v0.35.0/.venv");
  var cancellation=new VideoMediaProcessCancellation();
  var hook=new Thread(cancellation::cancel);Runtime.getRuntime().addShutdownHook(hook);
  try {
   var request=new VideoMediaProcessRequest(Path.of(args[0]),args[1],Arrays.asList(Arrays.copyOfRange(args,5,args.length)),Path.of(args[2]),Duration.ofSeconds(Long.parseLong(args[3])),1048576,1048576,env,Long.parseLong(args[4]));
   var result=new VideoMediaProcess().run(request,cancellation);
   System.out.print(result.getStdout().getText());System.err.print(result.getStderr().getText());System.out.println("OWNED_MEDIA_ELAPSED="+result.getElapsed());
  } catch(VideoMediaProcessException e){System.out.print(e.getStdout().getText());System.err.print(e.getStderr().getText());throw e;}
  finally{Runtime.getRuntime().removeShutdownHook(hook);}
 }
}
