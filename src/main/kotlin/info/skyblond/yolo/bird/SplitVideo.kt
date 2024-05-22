package info.skyblond.yolo.bird

import java.io.File
import java.io.FileFilter
import java.time.Duration

private val duration = Duration.ofMinutes(1)
private const val extension = "mp4"
private const val smallFileSize = 1024 * 1024 // 1MB

// TODO: Concat the clips for the same day
fun main() {
    val rootFolder = File("D:\\modet")
    val sourceFolder = File(rootFolder, "raw").also { it.mkdirs() }
    val processedFolder = File(sourceFolder, "processed").also { it.mkdirs() }
    val outputFolder = File(rootFolder, "chunk").also { it.mkdirs() }

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
        // moved to processed folder
        f.copyTo(File(processedFolder, f.name), true)
        p.waitFor()
        // delete old file after finished
        f.delete()
    }

    // delete small file
    outputFolder.listFiles(FileFilter { it.isFile && it.extension == extension })!!.forEach {
        if (it.length() < smallFileSize) {
            it.delete()
            return@forEach
        }
    }
}
