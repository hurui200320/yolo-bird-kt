package info.skyblond.yolo.bird

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession.SessionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.time.measureTime

private const val confidenceThreshold = 0.6f
private const val nmsThreshold = 0.7
private const val skip = 6 // take 1 frame out of `skip` frames

private val aws = !System.getProperty("os.name").lowercase().contains("windows")

private val rootFolder = if (aws) File("/workspace") else File("D:/modet")
private val inputFolder = File(rootFolder, "chunk")
private val interestFolder = File(rootFolder, "bird").also { it.mkdirs() }
private val evidenceFolder = File(rootFolder, "evidence").also { it.mkdirs() }
private val model = if (aws) File("/workspace/yolov8x.onnx") else File("D:\\code\\PycharmProjects\\yolov8\\yolov8l.onnx")

private val interestLabels = listOf("bird")

fun main(): Unit = runBlocking(Dispatchers.Default) {
    if (aws) println("We're running on AWS EC2!")
    disableFFmpegLog()
    println("Loading model...")
    val env = OrtEnvironment.getEnvironment()
    val yoloV8 = YOLOv8(env, cocoNames, model) {
        addCUDA()
        setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
        setExecutionMode(SessionOptions.ExecutionMode.PARALLEL)
    }
    println("Load model done!")
    println(
        "Model input: " +
                "width ${yoloV8.inputWidth}, " +
                "height ${yoloV8.inputHeight}, " +
                "channel ${yoloV8.inputChannel}, " +
                "batch size ${yoloV8.batchSize}"
    )
    println("Model output labels ${yoloV8.outputLabelSize - 4}")

    // load some video files in channel
    val videoChannel = readVideoFiles(inputFolder, 1, skip)

    for ((file, video) in videoChannel) {
        println("Processing $file, total frame: ${video.size}")
        println("*".repeat(ceil(video.size / yoloV8.batchSizeInt.toDouble()).toInt()))

        val time = measureTime {
            val result = yoloV8.doInference(video, confidenceThreshold, nmsThreshold) { l ->
                if (l.flatten().any { it.className in interestLabels })
                    print("+")
                else print("-")
            }
            println()
            val (interestFrame, interestDetections) = video.zip(result)
                .filter { (_, d) -> d.any { it.className in interestLabels } }
                .maxByOrNull { (_, d) -> d.maxOf { it.confidence } }
                ?: (null to emptyList())

            if (interestFrame != null) {
                println("Interest detected!")
                launch(Dispatchers.IO) {
                    interestDetections.filter { it.className in interestLabels }
                        .sortedBy { it.confidence }
                        .forEach { it.drawOn(interestFrame) }
                    ImageIO.write(interestFrame, "png", File(evidenceFolder, file.nameWithoutExtension + ".png"))
                }
            } else {
                println("Boring!")
            }
            launch(Dispatchers.IO) {
                if (interestFrame != null)
                    file.copyTo(File(interestFolder, file.name), overwrite = true)
                file.delete()
            }
        }

        println("Time usage: $time, ${"%.4f".format(video.size.toDouble() / time.inWholeSeconds)} fps")
        System.gc()
    }

    yoloV8.close()
    env.close()
}
