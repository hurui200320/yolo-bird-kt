package info.skyblond.yolo.bird

import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.model.Oklch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
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

fun CoroutineScope.readVideoFiles(
    inputFolder: File, buffer: Int, skip: Int
): ReceiveChannel<Pair<File, List<BufferedImage>>> {
    val channel = Channel<Pair<File, List<BufferedImage>>>(buffer)
    launch {
        inputFolder.listFiles { it: File ->
            it.isFile && it.nameWithoutExtension.isNotBlank()
        }!!.forEach { file ->
            val frames = buildList {
                var counter = 0
                ffmpegVideoFrameChannel(file, Channel.UNLIMITED).consumeEach {
                    if (counter % skip == 0) add(it)
                    counter++
                }
            }
            channel.send(file to frames)
        }
        channel.close()
    }
    return channel
}

val cocoNames = listOf(
    "person", "bicycle", "car", "motorbike", "aeroplane",
    "bus", "train", "truck", "boat", "traffic light",
    "fire hydrant", "stop sign", "parking meter", "bench", "bird",
    "cat", "dog", "horse", "sheep", "cow",
    "elephant", "bear", "zebra", "giraffe", "backpack",
    "umbrella", "handbag", "tie", "suitcase", "frisbee",
    "skis", "snowboard", "sports ball", "kite", "baseball bat",
    "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
    "wine glass", "cup", "fork", "knife", "spoon",
    "bowl", "banana", "apple", "sandwich", "orange",
    "broccoli", "carrot", "hot dog", "pizza", "donut",
    "cake", "chair", "sofa", "pottedplant", "bed",
    "diningtable", "toilet", "tvmonitor", "laptop", "mouse",
    "remote", "keyboard", "cell phone", "microwave", "oven",
    "toaster", "sink", "refrigerator", "book", "clock",
    "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
)

private val colors: List<Color> by lazy {
    buildList {
        for (i in 0..360 step 20) {
            add(Oklch(0.7, 0.1, i.toDouble()))
        }
    }
}

fun String.getColor(): Color = colors[(hashCode() % colors.size).absoluteValue]
