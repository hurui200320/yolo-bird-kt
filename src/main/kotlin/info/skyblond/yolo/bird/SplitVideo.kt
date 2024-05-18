package info.skyblond.yolo.bird

import java.io.File
import java.io.FileFilter
import java.time.Duration

private val duration = Duration.ofMinutes(1)
private const val extension = "mp4"
private const val smallFileSize = 1024 * 1024 // 1MB

fun main() {
    val sourceFolder = File("D:\\modet")
    val outputFolder = File(sourceFolder, "chunk").also { it.mkdirs() }

    sourceFolder.listFiles(FileFilter { it.isFile && it.extension == extension })!!.forEach { f ->
        val p = ProcessBuilder(
            listOf(
                "ffmpeg", "-i", f.absolutePath,
                "-f", "segment", "-segment_time", duration.toSeconds().toString(),
                "-vcodec", "copy", "-acodec", "copy",
                "-reset_timestamps", "1", "-map", "0",
                File(outputFolder, f.nameWithoutExtension + "_%d.$extension").absolutePath
            )
        )
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .start()
        p.waitFor()
        f.delete()
    }

    outputFolder.listFiles()!!.forEach {
        if (it.extension != extension) return@forEach
        if (it.length() < smallFileSize) {
            it.delete()
            return@forEach
        }
    }
}
