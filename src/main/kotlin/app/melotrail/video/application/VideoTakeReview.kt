package app.melotrail.video.application

import app.melotrail.video.domain.VideoProject
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
        val current = reopen(session, expectedRevision)
        // Reopen validates the document and every take artifact before recording a decision.
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

    /** Selection is an explicit project reference change, not a review decision. */
    fun select(
        session: VideoProjectSession,
        expectedRevision: Long,
        takeIds: List<VideoVersionedId>,
    ): VideoTakeReviewResult {
        val current = reopen(session, expectedRevision)
        require(takeIds.distinct().size == takeIds.size) { "Selected take versions must be unique" }
        require(takeIds.all { id -> current.takeVersions.any { it.id == id } }) {
            "Selection needs existing take versions"
        }
        require(takeIds.none { current.reviewStatus(it) == VideoTakeReviewStatus.REJECTED }) {
            "A rejected take cannot be selected"
        }
        // save rechecks the revision and verifies every artifact under its document lock.
        // A failed member or a concurrent review fails the entire transaction.
        val next = current.copy(selectedTakeIds = takeIds.toList(), revision = Math.addExact(expectedRevision, 1L))
        val saved = projects.save(session.root, expectedRevision, next)
        return VideoTakeReviewResult(VideoProjectSession(session.root, saved), null, null)
    }

    private fun reopen(session: VideoProjectSession, expectedRevision: Long): VideoProject {
        require(expectedRevision >= 0 && session.project.revision == expectedRevision) {
            "Reopen the video project before changing a stale revision"
        }
        val current = projects.open(session.root)
        require(current.id == session.project.id) { "The video project identity changed; reopen it" }
        if (current.revision != expectedRevision) {
            throw VideoProjectConcurrencyException("The video project changed; reopen it before changing a take")
        }
        return current
    }
}

data class VideoTakeReviewResult(
    val session: VideoProjectSession,
    /** Null for selection, which must not create an approval or review event. */
    val event: VideoTakeReviewEvent?,
    /** Null for selection; inspect the project for each take's independent review status. */
    val status: VideoTakeReviewStatus?,
)
