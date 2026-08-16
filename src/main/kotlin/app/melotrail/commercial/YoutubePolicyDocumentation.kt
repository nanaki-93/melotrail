package app.melotrail.commercial

import java.nio.file.Files
import java.nio.file.Path

/** Offline release gate: a human must refresh the dated policy review before shipping. */
object YoutubePolicyDocumentation {
    const val AI_DISCLOSURE_URL = "https://support.google.com/youtube/answer/14328491"
    const val MONETIZATION_URL = "https://support.google.com/youtube/answer/1311392"
    fun requireReviewed(document: Path, requiredDate: String) {
        val text = Files.readString(document)
        require(text.contains(AI_DISCLOSURE_URL) && text.contains(MONETIZATION_URL)) { "Commercial policy document must link both official YouTube policies." }
        require(text.contains("Policy review date: $requiredDate")) { "Commercial policy document must be reviewed on $requiredDate before release." }
    }
}
