package info.skyblond.yolo.bird

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession.SessionOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.ceil
import kotlin.time.measureTime

private const val confidenceThreshold = 0.35f // 0.7f
private const val nmsThreshold = 0.7 // 0.4
private const val skip = 4 // take 1 frame out of `skip` frames

private val aws = !System.getProperty("os.name").lowercase().contains("windows")

private val rootFolder = if (aws) File("/workspace") else File("D:/modet")
private val inputFolder = File(rootFolder, "chunk")
private val interestFolder = File(rootFolder, "bird").also { it.mkdirs() }
private val boringFolder = File(rootFolder, "boring").also { it.mkdirs() }
private val model = if (aws) File("/workspace/yolov8x.onnx") else File("D:\\code\\PycharmProjects\\yolov8\\yolov8l.onnx")

private val interestLabels = listOf("bird")

fun main(): Unit = runBlocking {
    if (aws) println("We're running on AWS EC2!")
    disableFFmpegLog()
    println("Loading model...")
    val env = OrtEnvironment.getEnvironment()
    val yoloV8 = YOLOv8(env, model, File("coco.names")) {
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
        println("+".repeat(ceil(video.size / yoloV8.batchSizeInt.toDouble()).toInt()))

        val time = measureTime {
            val result = yoloV8.doInference(video, confidenceThreshold, nmsThreshold) {
                print("*")
            }
            val hasBird = result.flatten().any { it.className in interestLabels }
            System.gc()
            println()
            print(if (hasBird) "Interest detected!" else "Boring...")
            file.copyTo(
                File(if (hasBird) interestFolder else boringFolder, file.name),
                overwrite = true
            )
            file.delete()
        }

        println("Time usage: $time, ${"%.4f".format(video.size.toDouble() / time.inWholeSeconds)} fps")
        System.gc()
    }

    yoloV8.close()
    env.close()
}
