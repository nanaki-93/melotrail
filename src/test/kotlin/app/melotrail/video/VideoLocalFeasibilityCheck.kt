package app.melotrail.video

import app.melotrail.video.adapter.LocalVideoProfileBoundary
import app.melotrail.video.adapter.LocalVideoProbeRunStatus
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** V11 preparation or explicit real-host probe entry point, selected by the request execution block. */
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
        if (request.execution == null) {
            println(LocalVideoProfileBoundary.encodeReport(LocalVideoProfileBoundary.prepare(profile, request)))
        } else {
            val report = LocalVideoProfileBoundary.run(profile, request)
            println(LocalVideoProfileBoundary.encodeReport(report))
            check(report.status == LocalVideoProbeRunStatus.COMPLETED_UNREVIEWED) {
                "Local video probe failed: ${report.reason}; report preserved at ${request.reportPath}"
            }
        }
    }
}
