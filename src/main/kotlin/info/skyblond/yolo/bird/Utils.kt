package info.skyblond.yolo.bird

import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.model.Oklch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.absoluteValue

fun disableFFmpegLog() {
    avutil.av_log_set_level(avutil.AV_LOG_QUIET)
}

fun CoroutineScope.ffmpegVideoFrameChannel(
    video: File, bufferedFrame: Int
): ReceiveChannel<BufferedImage> {
    val channel = Channel<BufferedImage>(capacity = bufferedFrame)
    launch(Dispatchers.Default) {
        val grabber = FFmpegFrameGrabber(video)
        // converter need this format
        grabber.pixelFormat = AV_PIX_FMT_BGR24
        val converter = Java2DFrameConverter()

        grabber.start()
        while (true) {
            // the frame is reused internally in grabber
            val frame = grabber.grabImage() ?: break
            // the image is reused internally in converter, thus it's unsafe
            val unsafeImage = converter.getBufferedImage(frame)
            // copy the image
            val safeImage = Java2DFrameConverter.cloneBufferedImage(unsafeImage)
            // yield the safe copy
            try {
                channel.send(safeImage)
            } catch (e: CancellationException) {
                break
            }
        }
        grabber.close()
        converter.close()
        channel.close()
    }
    return channel
}

private val colors: List<Color> by lazy {
    buildList {
        for (i in 0..360 step 10) {
            add(Oklch(0.7, 0.1, i.toDouble()))
        }
    }
}

fun String.getColor(): Color = colors[(hashCode() % colors.size).absoluteValue]
