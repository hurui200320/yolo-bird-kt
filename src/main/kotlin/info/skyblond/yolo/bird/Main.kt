package info.skyblond.yolo.bird

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession.SessionOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.ceil
import kotlin.time.measureTime
import kotlin.time.toDuration

private const val confidenceThreshold = 0.7f
private const val nmsThreshold = 0.4

private val rootFolder = File("D:/modet")
private val inputFolder = File(rootFolder, "chunk")
private val birdFolder = File(rootFolder, "bird").also { it.mkdirs() }
private val boringFolder = File(rootFolder, "boring").also { it.mkdirs() }

fun main(): Unit = runBlocking {
    disableFFmpegLog()
    println("Loading model...")
    val env = OrtEnvironment.getEnvironment()
    val yoloV8 = YOLOv8(
        env,
        File("D:\\code\\PycharmProjects\\yolov8\\yolov8x.onnx"),
        File("coco.names")
    ) {
        addCUDA()
        setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
        setExecutionMode(SessionOptions.ExecutionMode.PARALLEL)
    }
    println("Load model done!")

    // load some video files in channel
    val videoChannel = readVideoFiles(inputFolder, 2)

    for ((file, video) in videoChannel) {
        val time = measureTime {
            println("Processing $file, total frame: ${video.size}")
            println(".".repeat(ceil(video.size / yoloV8.batchSizeInt.toDouble()).toInt()))
            var counter = 0
            val hasBird = video.chunked(yoloV8.batchSizeInt).any { batch ->
                val result = yoloV8.doInference(batch, confidenceThreshold, nmsThreshold)
                counter += batch.size
                print(".")
                result.flatten().any { it.className == "bird" }.also { System.gc() }
            }
            println()
            print(if (hasBird) "Bird detected!" else "Boring...")
            file.copyTo(
                File(if (hasBird) birdFolder else boringFolder, file.name),
                overwrite = true
            )
            file.delete()
        }
        println("Time consumed: $time, ${video.size.toDouble() / time.inWholeSeconds} fps")
        System.gc()
    }

    yoloV8.close()
    env.close()
}

fun CoroutineScope.readVideoFiles(inputFolder: File, buffer: Int): ReceiveChannel<Pair<File, List<BufferedImage>>> {
    val channel = Channel<Pair<File, List<BufferedImage>>>(buffer)
    launch {
        inputFolder.listFiles { it: File -> it.isFile }!!.forEach { file ->
            val frames = buildList {
                var counter = 0
                ffmpegVideoFrameChannel(file, Channel.UNLIMITED).consumeEach {
                    if (counter % 4 == 0) add(it)
                    counter++
                }
            }
            channel.send(file to frames)
        }
        channel.close()
    }
    return channel
}
