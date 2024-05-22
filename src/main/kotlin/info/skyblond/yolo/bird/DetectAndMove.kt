package info.skyblond.yolo.bird

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession.SessionOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.ceil
import kotlin.time.measureTime

private const val confidenceThreshold = 0.25f // 0.7f
private const val nmsThreshold = 0.7 // 0.4
private const val skip = 8 // take 1 frame out of 8

private val aws = System.getProperty("os.name").lowercase().contains("windows")

private val rootFolder = if (aws) File("/opt/dlami/nvme") else File("D:/modet")
private val inputFolder = File(rootFolder, "chunk")
private val interestFolder = File(rootFolder, "bird").also { it.mkdirs() }
private val boringFolder = File(rootFolder, "boring").also { it.mkdirs() }
private val model = if (aws) File("/opt/dlami/nvme/yolov8x.onnx") else File("D:\\code\\PycharmProjects\\yolov8\\yolov8l.onnx")

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
        var counter = 0
        val time = measureTime {
            println("Processing $file, total frame: ${video.size}")
            println("+".repeat(ceil(video.size / yoloV8.batchSizeInt.toDouble()).toInt()))
            val hasBird = video.chunked(yoloV8.batchSizeInt).any { batch ->
                val result = yoloV8.doInference(batch, confidenceThreshold, nmsThreshold)
                counter += batch.size
                print("*")
                result.flatten().any { it.className in interestLabels }.also { System.gc() }
            }
            println()
            print(if (hasBird) "Interest detected!" else "Boring...")
            file.copyTo(
                File(if (hasBird) interestFolder else boringFolder, file.name),
                overwrite = true
            )
            file.delete()
        }
        println(" Processed $counter frames, time: $time, ${"%.4f".format(counter.toDouble() / time.inWholeSeconds)} fps")
        System.gc()
    }

    yoloV8.close()
    env.close()
}
