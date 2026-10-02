import app.melotrail.video.ComfyVideoHostProbe;
import app.melotrail.video.adapter.*;
import app.melotrail.video.application.*;
import app.melotrail.video.domain.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** One explicitly authorized pilot; reuses production admission/runtime/media boundaries. */
public final class ActionPilot {
    static Path directory(Path path) throws Exception {
        return Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }
    static String digest(Path path) throws Exception {
        var hash = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) hash.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(hash.digest());
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    static VideoJobResult.Accepted accepted(VideoJobResult value) {
        if (!(value instanceof VideoJobResult.Accepted result)) throw new IllegalStateException("Request rejected: " + value);
        return result;
    }
    public static void main(String[] args) throws Exception {
        require(args.length == 1, "Select exactly one request");
        var requestPath = Path.of(args[0]);
        var host = ComfyVideoHostProbe.INSTANCE;
        var selected = host.validateRequest$melotrail_test(requestPath);
        require(selected.getWidth() == 768 && selected.getHeight() == 448 && selected.getExpectedFrameCount() == 129 && selected.getFramesPerSecond() == 25,
            "Only the authorized 768x448, 129-frame / 25-fps case is allowed");
        var output = directory(selected.getOutputDirectory());
        var setup = LocalVideoSetup.Companion.bundled();
        var initial = setup.inspect(selected.getApplicationSupportRoot());
        require(initial.getState() == LocalVideoSetupState.READY, "Pinned setup not ready: " + initial.getIssues());
        var requestHash = digest(requestPath);
        var imageHash = digest(selected.getComposedImage());
        var graphDirectory = directory(output.resolve("workflow-input"));
        var graph = graphDirectory.resolve("guided-drink-api.json");
        var authoredGraph = requestPath.getParent().resolve("workflow/guided-drink-api.json");
        require(digest(authoredGraph).equals("b6edaa2fb1b6787be2c856ff443a580f649783a69073f0429bad10034c022e11"), "Guided workflow changed");
        Files.copy(authoredGraph, graph);
        var graphHash = digest(graph);
        var store = new VideoJobStore(output.resolve("jobs"), selected.getRunId(), selected.getProtectedMidiRoots(),
            VideoJobAtomicWriteObserver.Companion.getNONE());
        var publication = directory(output.resolve("publication"));
        var models = List.of(
            new VideoModelRequirement("ltx-2-3-distilled-q4", "22b-distilled-1.1-Q4_K_M", "5d09efdc0b8ec2054c44a05366cd7c6634ffa333b379b1f8baf018a78974b73d"),
            new VideoModelRequirement("ltx-2-3-connectors", "distilled-1.1", "c61cbb396e2a8175d8b2da51f0fdac885a4ccd22c9f64dafa5aa2c455dc8a507"),
            new VideoModelRequirement("ltx-2-3-video-vae", "distilled-1.1", "e68d6d8f8a42942ac9b862cc315beb3bc30805a8876c7ad63ba5bf7a2b8e168a"),
            new VideoModelRequirement("gemma-3-12b-it-qat", "Q4_K_XL", "da98f81c86916ed1c76b3eeda56b25cb7b8352b01093e2edb8028110fe2cb53b"));
        var guide = selected.getComposedImage().getParent().resolve("approved-drinking-guide.png");
        var guideHash = digest(guide);
        require(guideHash.equals("d2d0f379211e12b4d3d7d3866efd8ad05a5c6066b4801bb8bcb28414635cd27c"), "Approved drinking guide changed");
        var binding = new VideoComfyWorkflowRequest("vg2-25-guided-drink-graph", new VideoComfyInputSlot("20", "value"),
            List.of(new VideoComfyReferenceInput("start-scene", new VideoComfyInputSlot("4", "image"), "reading-scene.png"),
                    new VideoComfyReferenceInput("drink-guide", new VideoComfyInputSlot("30", "image"), "drinking-guide.png")),
            new VideoComfyInputSlot("21", "value"), new VideoComfyInputSlot("22", "value"),
            new VideoComfyInputSlot("23", "value"), new VideoComfyInputSlot("24", "value"),
            new VideoComfyOutputBinding("16", Set.of("mp4")));
        var input = new VideoClipGenerationInput(selected.getPrompt(),
            List.of(new VideoGenerationDependencyPin("vg2-25-guided-drink-graph", graphHash, graph.toString()),
                    new VideoGenerationDependencyPin("start-scene", imageHash, selected.getComposedImage().toString()),
                    new VideoGenerationDependencyPin("drink-guide", guideHash, guide.toString())),
            selected.getDurationMillis(), selected.getWidth(), selected.getHeight(), selected.getFramesPerSecond(), binding, null);
        var job = new VideoGenerationJobRequest("vg2-25-drink", "vg2-25-motion-proof", LocalVideoBackend.BACKEND_ID,
            models, input, LocalVideoBackendKt.comfyRequestFingerprint("vg2-25-motion-proof", LocalVideoBackend.BACKEND_ID, input, models),
            1, Instant.now().toString(), new VideoLocalExecutionPolicy(1200000L, 48L*1024*1024*1024, 10L*1024*1024*1024));
        var runtime = new ComfyVideoRuntime();
        var shutdown = new Thread(runtime::close, "vg2-25-owned-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        VideoJobResult.Accepted result = null;
        long started = System.nanoTime();
        try {
            var session = runtime.start(initial.getReady(), selected.getApplicationSupportRoot().resolve("runs"), selected.getPort());
            System.out.println("SESSION=" + session.getDirectory());
            Files.writeString(output.resolve("session-path.txt"), session.getDirectory().toString(), StandardOpenOption.CREATE_NEW);
            var available = models.stream().map(m -> new VideoAvailableModel(m.getId(), m.getVersion(), m.getSha256())).toList();
            var backend = new LocalVideoBackend(runtime, session,
                id -> store.snapshot().getJobs().stream().map(VideoGenerationJob::getRequest).filter(r -> r.getId().equals(id)).findFirst().orElse(null),
                List.of(graphDirectory, selected.getComposedImage().getParent()), publication, available, Clock.systemUTC());
            var coordinator = new VideoJobCoordinator(selected.getRunId(), store, List.of(backend), List.of(), Clock.systemUTC(),
                () -> "attempt-" + UUID.randomUUID(), () -> "owner-" + UUID.randomUUID(), () -> "output-" + UUID.randomUUID());
            result = accepted(coordinator.submit(job));
            host.requireActiveSubmission$melotrail_test(result.getAttempt(), result.getLaunchedByCaller(), "VG2-25 drink proof");
            System.out.println("SUBMITTED=" + result.getAttempt().getId());
            long deadline = System.nanoTime() + Duration.ofMinutes(20).toNanos();
            int observations = 0;
            while (result.getAttempt() != null && !result.getAttempt().getStatus().isTerminal()) {
                require(System.nanoTime() < deadline, "Single pilot exceeded existing 20-minute bound");
                require(runtime.getState() == ComfyVideoRuntimeState.READY,
                    "Owned runtime left READY: " + runtime.getLastFailure());
                Thread.sleep(selected.getPollIntervalMillis());
                result = accepted(coordinator.reconcile(job.getId(), result.getAttempt().getId()));
                if (++observations % 15 == 0) System.out.println("OBSERVATION=" + observations + " state=" + result.getAttempt().getStatus());
            }
            require(result.getAttempt() != null && result.getAttempt().getStatus() == VideoGenerationAttemptStatus.SUCCEEDED,
                "Single pilot did not succeed: " + (result.getAttempt() == null ? "missing attempt" : result.getAttempt().getFailure()));
            require(result.getJob().getOutputs().size() == 1, "Expected one immutable output");
            var artifact = result.getJob().getOutputs().getFirst();
            var video = publication.resolve(artifact.getRelativePath()).normalize();
            require(video.startsWith(publication) && Files.isRegularFile(video) && digest(video).equals(artifact.getSha256()), "Published output is missing or changed");
            // No second submit and no cancellation-test inference: the user authorized one generation only.
            runtime.stop();
            require(setup.inspect(selected.getApplicationSupportRoot()).getState() == LocalVideoSetupState.READY, "Installed pins changed");
            require(digest(requestPath).equals(requestHash) && digest(selected.getComposedImage()).equals(imageHash) && digest(graph).equals(graphHash), "An input changed");
            var media = new VideoMediaProbe().run(new VideoMediaProbeRequest(selected.getMediaToolsDirectory(), video,
                output.resolve("media-proof"), Duration.ofMinutes(2)), new VideoMediaProcessCancellation());
            var facts = media.getInputMetadata();
            require(Objects.equals(facts.getDecodedFrameCount(), 129L) && facts.getWidth() == 768 && facts.getHeight() == 448 &&
                facts.getAudioStreamCount() == 0 && Math.abs(facts.getFrameRate() - 25) < 0.01 && Math.abs(facts.getDurationSeconds() - 5.16) < 0.08,
                "Unexpected decoded media facts: " + facts);
            Files.writeString(output.resolve("published-video.txt"), video.toString(), StandardOpenOption.CREATE_NEW);
            System.out.println("ACTION_PILOT_PASSED video=" + video + " sha256=" + digest(video));
            System.out.println("MEDIA_FACTS=" + facts);
            System.out.println("MEDIA_REPORT=" + media.getReport());
        } catch (Throwable failure) {
            Files.writeString(output.resolve("failure.txt"), failure + "\nruntimeState=" + runtime.getState() +
                "\nruntimeFailure=" + runtime.getLastFailure() + "\nelapsedMillis=" + Duration.ofNanos(System.nanoTime()-started).toMillis() + "\n", StandardOpenOption.CREATE_NEW);
            throw failure;
        } finally {
            runtime.close();
            Runtime.getRuntime().removeShutdownHook(shutdown);
            System.out.println("RUNTIME_FINAL=" + runtime.getState());
            System.out.println("ELAPSED_MILLIS=" + Duration.ofNanos(System.nanoTime()-started).toMillis());
            System.out.println("REQUEST_UNCHANGED=" + digest(requestPath).equals(requestHash));
            System.out.println("IMAGE_UNCHANGED=" + digest(selected.getComposedImage()).equals(imageHash));
            System.out.println("GRAPH_UNCHANGED=" + digest(graph).equals(graphHash));
            require(digest(guide).equals(guideHash), "Drinking guide changed");
            System.out.println("GUIDE_UNCHANGED=true");
        }
    }
}
