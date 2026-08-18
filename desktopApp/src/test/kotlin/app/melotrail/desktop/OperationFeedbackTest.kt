package app.melotrail.desktop

import app.melotrail.application.OperationProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationFeedbackTest {
    @Test
    fun `active operation transitions retain a stable session and known work only`() {
        val tracker = OperationFeedbackTracker()
        val started = tracker.begin(OperationKind.IMPORT, OperationPhase.VALIDATING, "Validating import")
        val progressed = assertNotNull(tracker.progress(started.sessionId, OperationPhase.WAITING_FOR_WORKER, "Copying source", 1, 4))

        assertEquals(started.sessionId, progressed.sessionId)
        assertEquals(OperationWork(1, 4), progressed.work)
        assertTrue(progressed.determinate)
        assertNull(tracker.progress(started.sessionId, OperationPhase.WAITING_FOR_WORKER, "Worker has no count", 1, null)?.work)
    }

    @Test
    fun `completion failure and stale callbacks are typed and deterministic`() {
        val tracker = OperationFeedbackTracker()
        val old = tracker.begin(OperationKind.INSPECTION, OperationPhase.WAITING_FOR_WORKER, "Inspecting")
        val current = tracker.begin(OperationKind.TRANSCRIPTION, OperationPhase.WAITING_FOR_WORKER, "Transcribing")

        assertNull(tracker.complete(old.sessionId, "Inspection complete"))
        assertNull(tracker.fail(old.sessionId, "Inspection failed"))
        val failed = assertNotNull(tracker.fail(current.sessionId, "Worker unavailable", OperationRetryAction.RETRY_SAFE_OPERATION))
        assertEquals(OperationPhase.FAILED, failed.phase)
        assertEquals(OperationSeverity.ERROR, failed.outcomeSeverity)
        assertEquals(OperationRetryAction.RETRY_SAFE_OPERATION, failed.retryAction)
        assertFalse(failed.active)
    }

    @Test
    fun `only explicitly cancellable work accepts cancellation at a safe boundary`() {
        val tracker = OperationFeedbackTracker()
        val import = tracker.begin(OperationKind.IMPORT, OperationPhase.WAITING_FOR_WORKER, "Importing")
        assertNull(tracker.cancelAtBoundary(import.sessionId, "Cancel"))

        val build = tracker.begin(OperationKind.MASTERING, OperationPhase.WAITING_FOR_WORKER, "Mastering", cancellableAtBoundary = true)
        val cancelling = assertNotNull(tracker.cancelAtBoundary(build.sessionId, "Finishing current boundary"))
        assertEquals(OperationPhase.CANCELLING, cancelling.phase)
        assertFalse(cancelling.cancellableAtBoundary)
    }

    @Test
    fun `service progress exposes every long backend boundary without message inference`() {
        val kinds = listOf(
            OperationKind.PROJECT_OPEN, OperationKind.PROJECT_HYDRATION, OperationKind.IMPORT, OperationKind.INSPECTION,
            OperationKind.AUDIO_CLEANUP, OperationKind.TRANSCRIPTION, OperationKind.MIDI_CLEANUP, OperationKind.MIDI_RENDER,
            OperationKind.PREVIEW_DECODE_RENDER, OperationKind.COHESION, OperationKind.ARRANGEMENT, OperationKind.APPROVAL,
            OperationKind.STEM_RENDER, OperationKind.MIXING, OperationKind.AUDIO_LOFI, OperationKind.MASTERING, OperationKind.EXPORT
        )
        assertEquals(OperationKind.STEM_RENDER, OperationProgressFeedbackKind(OperationProgress("build", 3, 9, "anything")))
        assertEquals(OperationKind.AUDIO_LOFI, OperationProgressFeedbackKind(OperationProgress("build", 6, 9, "anything")))
        assertEquals(OperationKind.EXPORT, OperationProgressFeedbackKind(OperationProgress("build", 8, 9, "anything")))
        assertEquals(OperationPhase.WAITING_FOR_RENDERER, OperationProgressFeedbackPhase(OperationProgress("build", 3, 9, "any text")))
        assertEquals(17, kinds.distinct().size)
    }
}
