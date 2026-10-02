import app.melotrail.video.adapter.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Scratch pilot wrapper around existing production setup/media supervision; no inference API. */
public final class MotionTools {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "request" -> {
                var checked = app.melotrail.video.ComfyVideoHostProbe.INSTANCE.validateRequest$melotrail_test(Path.of(args[1]));
                System.out.println("REQUEST_VALIDATED=" + checked);
            }
            case "setup" -> {
                var report = LocalVideoSetup.Companion.bundled().inspect(Path.of(args[1]));
                System.out.println("SETUP=" + report.getState());
                for (var issue : report.getIssues()) System.out.println(issue);
                for (var terms : report.getTerms()) System.out.println(terms);
                if (report.getState() != LocalVideoSetupState.READY) System.exit(2);
            }
            case "media" -> {
                var job = new VideoMediaProcessRequest(Path.of(args[1]), args[2],
                    Arrays.asList(Arrays.copyOfRange(args, 4, args.length)), Path.of(args[3]),
                    Duration.ofMinutes(3), 1048576, 1048576,
                    Map.of("LC_ALL", "C", "LANG", "C"), 4L * 1024 * 1024 * 1024);
                var result = new VideoMediaProcess().run(job, new VideoMediaProcessCancellation());
                System.out.print(result.getStdout().getText());
                System.err.print(result.getStderr().getText());
                System.err.println("OWNED_MEDIA_ELAPSED=" + result.getElapsed());
            }
            case "validate" -> {
                var result = new VideoMediaProbe().run(new VideoMediaProbeRequest(Path.of(args[1]),
                    Path.of(args[2]), Path.of(args[3]), Duration.ofMinutes(3)), new VideoMediaProcessCancellation());
                System.out.println(result.getInputMetadata());
                System.out.println("MEDIA_REPORT=" + result.getReport());
            }
            default -> throw new IllegalArgumentException("Unknown pilot operation");
        }
    }
}
