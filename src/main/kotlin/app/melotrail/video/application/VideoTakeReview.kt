package app.melotrail.video.application

import app.melotrail.video.domain.VideoTakeReviewDecision
import app.melotrail.video.domain.VideoTakeReviewEvent
import app.melotrail.video.domain.VideoTakeReviewStatus
import app.melotrail.video.domain.VideoVersionedId
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Explicit take decisions are independent of selection and of media provenance. */
class VideoTakeReview(
    private val projects: VideoProjectPersistence,
    private val clock: Clock = Clock.systemUTC(),
    private val eventId: () -> String = { "review-${UUID.randomUUID()}" },
) {
    fun review(
        session: VideoProjectSession,
        expectedRevision: Long,
        takeId: VideoVersionedId,
        decision: VideoTakeReviewDecision,
        reviewer: String,
        note: String?,
    ): VideoTakeReviewResult {
        require(expectedRevision >= 0 && session.project.revision == expectedRevision) {
            "Reopen the video project before reviewing a stale revision"
        }
        // Reopen validates the document and every take artifact before recording a decision.
        val current = projects.open(session.root)
        require(current.id == session.project.id) { "The video project identity changed; reopen it" }
        if (current.revision != expectedRevision) {
            throw VideoProjectConcurrencyException("The video project changed; reopen it before reviewing the take")
        }
        require(current.takeVersions.any { it.id == takeId }) { "Review needs an existing take version" }
        require(decision != VideoTakeReviewDecision.REJECTED || takeId !in current.selectedTakeIds) {
            "Explicitly deselect this take before rejecting it"
        }
        val event = VideoTakeReviewEvent(eventId(), takeId, decision, reviewer, Instant.now(clock).toString(), note)
        val next = current.copy(takeReviewEvents = current.takeReviewEvents + event,
            revision = Math.addExact(expectedRevision, 1L))
        val saved = projects.save(session.root, expectedRevision, next)
        return VideoTakeReviewResult(VideoProjectSession(session.root, saved), event, saved.reviewStatus(takeId))
    }
}

data class VideoTakeReviewResult(
    val session: VideoProjectSession,
    val event: VideoTakeReviewEvent,
    val status: VideoTakeReviewStatus,
)
