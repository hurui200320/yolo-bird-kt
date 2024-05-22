package info.skyblond.yolo.bird

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession.SessionOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil

private const val confidenceThreshold = 0.7f
private const val nmsThreshold = 0.4
private const val skip = 6

private val rootFolder = File("D:/modet/bird")
private val inputFolder = File(rootFolder, "wrong")
private val outputFolder = File(inputFolder, "debug").also { it.mkdirs() }
private val model = File("D:\\code\\PycharmProjects\\yolov8\\yolov8l.onnx")

private val interestLabels = listOf("bird")

fun main(): Unit = runBlocking {
    disableFFmpegLog()
    val env = OrtEnvironment.getEnvironment()
    val yoloV8 = YOLOv8(env, model, File("coco.names")) {
        addCUDA()
        setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
        setExecutionMode(SessionOptions.ExecutionMode.PARALLEL)
    }
    // load some video files in channel
    val videoChannel = readVideoFiles(inputFolder, 2, skip)

    for ((file, video) in videoChannel) {
        println("\nProcessing $file, total frame: ${video.size}")
        println("+".repeat(ceil(video.size / yoloV8.batchSizeInt.toDouble()).toInt()))
        val detection = video.chunked(yoloV8.batchSizeInt).firstNotNullOfOrNull { batch ->
            val result = yoloV8.doInference(batch, confidenceThreshold, nmsThreshold)
            print("*")
            batch.zip(result).filter { (_, dl) -> dl.any { it.className in interestLabels } }
                .ifEmpty { null }
        } ?: continue
        val (img, dl) = detection.first()
        dl.sortedBy { it.confidence }.forEach { it.drawOn(img) }
        ImageIO.write(img, "png", File(outputFolder, file.nameWithoutExtension + ".png"))

        System.gc()
    }

    yoloV8.close()
    env.close()
}
