package info.skyblond.yolo.bird

import com.github.ajalt.clikt.core.CliktCommand
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileFilter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration


/**
 * For every video file from [inputFolder] with [extension],
 * split into clips with [duration] length,
 * put the clip into [outputFolder],
 * put the original file into [processedFolder].
 * */
fun splitVideos(
    inputFolder: File, outputFolder: File, processedFolder: File,
    extension: String, duration: Duration, ffmpeg: String
) {
    inputFolder.listFiles(FileFilter {
        it.isFile && it.extension.lowercase() == extension.lowercase()
    })!!.forEach { file ->
        val p = ffmpeg.startFFmpeg(
            "-i", file.absolutePath, "-f", "segment",
            "-segment_time", duration.toSeconds().toString(),
            "-vcodec", "copy", "-acodec", "copy",
            "-reset_timestamps", "1", "-map", "0",
            File(outputFolder, file.nameWithoutExtension + "_%d.$extension").absolutePath
        )
        // moved to processed folder
        val dayFolder = File(
            processedFolder,
            file.nameWithoutExtension.split("_").take(2).joinToString("_")
        ).also { it.mkdirs() }
        p.waitFor()
        // delete old file after finished
        val newFile = File(dayFolder, file.name)
        val metaTime = file.getMetaTime()
        Files.move(file.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        newFile.setMetaTime(metaTime)
    }
}

/**
 * Concat the clips into one video by date.
 * Will find all possible dates in [inputFolder] with [extension],
 * concat the video and put the result in [outputFolder],
 * and delete the clips.
 * */
fun CliktCommand.concatVideos(
    inputFolder: File, outputFolder: File, extension: String,
    ffmpeg: String
) {
    // first groups all video files based on name and date
    val map = inputFolder.listFiles(FileFilter {
        it.isFile && it.extension.lowercase() == extension.lowercase()
    })!!.groupBy {
        // take 2: $name_YYYY-MM-DD
        it.nameWithoutExtension.split("_").take(2).joinToString("_")
    }

    // for each name_date
    map.keys.forEach { key ->
        echoInfo("Concat $key...")
        val files = map[key]!!
        // prepare list for ffmpeg
        val listContent = files.sortedBy {
            val l = it.nameWithoutExtension.split("_")
            // l[2]: HH-MM => HHMM
            //   or: HH-MM-SS => HHMMSS
            val time = l[2].replace("-", "").toLong()
            val id = l[3].toLong()
            // leave 9 digits for clip id
            // good enough for slicing a single 1950 years long video into 60s clips
            time * 1_000_000_000 + id
        }.joinToString("\n") { "file \'${it.absolutePath}\'" }
        // prepare the list file for ffmpeg
        val listFile = File.createTempFile("ffmpeg-list", "txt")
        listFile.deleteOnExit()
        listFile.writeText(listContent)
        // call ffmpeg
        ffmpeg.startFFmpeg(
            "-f", "concat", "-safe", "0",
            "-i", listFile.absolutePath,
            "-c", "copy",
            File(outputFolder, "$key.$extension").absolutePath
        ).waitFor()
        // clean up
        listFile.delete()
        // delete clip files
        files.forEach { it.delete() }
    }
}

/**
 * Call ffmpeg. Default options:
 * + Overwrite existing (`-y`)
 * + Less output (`-hide_banner` and `-loglevel error`)
 *
 * Use [this] as the path to ffmpeg.
 *
 * @return the process, already started
 * */
private fun String.startFFmpeg(vararg args: String) = ProcessBuilder(
    listOf(
        this, "-y", "-hide_banner", "-loglevel", "error"
    ) + args
).also {
    it.redirectError(ProcessBuilder.Redirect.INHERIT)
    it.redirectOutput(ProcessBuilder.Redirect.INHERIT)
}.start()


/**
 * Grab all video frames from [video] file.
 * Take 1 frame in every [skip] frames.
 * For example, skip is 8, means take 1 frame, skip 7, then take 1, skip 7...
 * */
fun grabFrames(
    video: File, skip: Int
): List<BufferedImage> = buildList {
    avutil.av_log_set_level(avutil.AV_LOG_ERROR)
    val grabber = FFmpegFrameGrabber(video)
    // converter needs this format
    grabber.pixelFormat = AV_PIX_FMT_BGR24
    val converter = Java2DFrameConverter()
    var counter = 0L
    grabber.start()
    while (true) {
        // the frame is reused internally in grabber
        val frame = grabber.grabImage() ?: break
        // this will skip the first skip-1 frames
        // it is ideal for detecting bird
        // since it can skip the clips that the birds
        // only showing up in the first several frames
        counter++
        if (counter % skip != 0L) continue

        // the image is reused internally in converter, thus it's unsafe
        val unsafeImage = converter.getBufferedImage(frame)
        // copy the image
        val safeImage = Java2DFrameConverter.cloneBufferedImage(unsafeImage)
        // yield the safe copy
        add(safeImage)
    }

    grabber.close()
    converter.close()
}
