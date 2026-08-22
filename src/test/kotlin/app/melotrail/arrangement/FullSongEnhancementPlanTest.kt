package app.melotrail.arrangement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FullSongEnhancementPlanTest {
    private val hash = "a".repeat(64)
    private val issue = "b".repeat(32)
    private val note = "n-" + "c".repeat(64)

    @Test fun `strict parser accepts every allow-listed operation`() {
        FullSongEnhancementOperationKind.entries.forEach { kind ->
            val plan = FullSongEnhancementPlanParser.parse("""{"schemaVersion":1,"inputSha256":"$hash","contextSha256":"$hash","criticInputSha256":"$hash","criticReportSha256":"$hash","modelIdentity":"fake-v1","operations":[{"kind":"${kind.name}","issueId":"$issue","targetId":"bass","noteId":"$note","pitch":48}]}""")
            assertEquals(kind, plan.operations.single().kind)
        }
    }

    @Test fun `parser rejects prose and unknown fields`() {
        assertFailsWith<IllegalArgumentException> { FullSongEnhancementPlanParser.parse("here is a plan") }
        assertFailsWith<IllegalArgumentException> {
            FullSongEnhancementPlanParser.parse("""{"schemaVersion":1,"inputSha256":"$hash","contextSha256":"$hash","criticInputSha256":"$hash","criticReportSha256":"$hash","modelIdentity":"fake-v1","operations":[],"unsafe":"path"}""")
        }
    }

    @Test fun `policy uses exact floors including zero`() {
        val policy = FullSongEnhancementPolicy()
        assertEquals(0, policy.totalBudget(19)); assertEquals(1, policy.totalBudget(20))
        assertEquals(0, policy.additionDeletionBudget(49)); assertEquals(1, policy.additionDeletionBudget(50))
    }
}
