package app.melotrail.video

import app.melotrail.video.adapter.LocalVideoProfileBoundary
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** V11a preparation entry point. Real process invocation and measurements remain owned by V11. */
object VideoLocalFeasibilityCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) {
            "Supply an absolute request file with -PvideoProbeRequest=/absolute/path/to/file.json"
        }
        val requestPath = Path.of(args.single())
        require(requestPath.isAbsolute) {
            "videoProbeRequest must be absolute: $requestPath"
        }
        require(!Files.isSymbolicLink(requestPath) && Files.isRegularFile(requestPath, NOFOLLOW_LINKS)) {
            "videoProbeRequest is missing or is not a regular non-symbolic-link file: $requestPath"
        }
        val profile = LocalVideoProfileBoundary.loadBundledProfile()
        val request = LocalVideoProfileBoundary.decodeRequest(Files.readString(requestPath))
        val report = LocalVideoProfileBoundary.prepare(profile, request)
        println(LocalVideoProfileBoundary.encodeReport(report))
    }
}
