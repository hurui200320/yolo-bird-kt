package info.skyblond.yolo.bird

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.io.File
import java.nio.FloatBuffer
import java.util.*
import javax.imageio.ImageIO


private const val yoloWidth = 1920
private const val yoloHeight = 1088
private const val yoloChannel = 3
private const val yoloBatchSize = 3
private const val yoloInputName = "images"
private const val reportTimer = 2000

private const val confidenceThreshold = 0.5f
private const val nmsThreshold = 0.4

private val outputFolder = File("./output").also { it.mkdirs() }

fun main(): Unit = runBlocking {
    println("Loading model...")
    val (env, session) = setupOrt(
        File("D:\\code\\PycharmProjects\\yolov8\\yolov8x.onnx"),
        enableCUDA = true, allocateAllRAM = true
    )
    val names = loadNames("coco.names")
    println("Load model done!")

    // at least 3 * yoloBatchSize
    val frameChannel = Channel<BufferedImage>(capacity = 50)
    val inputTensorChannel = Channel<Pair<List<BufferedImage>, OnnxTensor>>(capacity = 20)
    val detectResultChannel = Channel<Pair<BufferedImage, List<Detection>>>()

    launch { // grabber thread
        val (grabber, converter) = setupFFmpeg(File("D:\\modet\\near_2024-05-13_15-00_0_1.mp4"))
        grabber.start()
        var counter = 0L
        var timer = 0L
        var discardCounter = 0L
        while (true) {
            val image = grabber.grabImage()?.use {
                // the converter will keep using the same buffered image
                // so the new frame will overwrite the old one
                val unsafeImage = converter.getBufferedImage(it)
                val safeImage = BufferedImage(unsafeImage.width, unsafeImage.height, unsafeImage.type)
                unsafeImage.copyData(safeImage.raster)
                safeImage
            } ?: break

            // drop half of the frame
            if (discardCounter % 2 == 0L) {
                frameChannel.send(image)
                counter++
            }
            discardCounter++

            val time = System.currentTimeMillis()
            val dt = time - timer
            if (dt > reportTimer) {
                timer = time
                println("Grabber: ${counter * 1000.0 / dt} fps")
                counter = 0
            }
        }
        frameChannel.close()
        grabber.close()
    }

    launch { // take frame and pack into input tensor
        var counter = 0L
        var timer = 0L
        val buffer = ArrayList<BufferedImage>(yoloBatchSize)
        for (frame in frameChannel) {
            if (buffer.size >= yoloBatchSize) {
                val images = ArrayList(buffer)
                buffer.clear()
                val t = makeInputFromImage(images, env)
                inputTensorChannel.send(images to t)
                counter++

                val time = System.currentTimeMillis()
                val dt = time - timer
                if (dt > reportTimer) {
                    timer = time
                    println("Tensor: ${counter * 1000.0 * yoloBatchSize / dt} fps")
                    counter = 0
                }
            }
            buffer.add(frame)
        }
        inputTensorChannel.close()
    }

    launch { // take the input tensor and do the thing
        var counter = 0L
        var timer = 0L
        for ((images, tensor) in inputTensorChannel) {
            val result = doDetect(session, tensor, names)
            tensor.close()
            System.gc()
            images.forEachIndexed { index, img ->
                val r = result[index]
                detectResultChannel.send(img to r)
                counter++

                val time = System.currentTimeMillis()
                val dt = time - timer
                if (dt > reportTimer) {
                    timer = time
                    println("Model: ${counter * 1000.0 / dt} fps")
                    counter = 0
                }
            }
        }
        detectResultChannel.close()
    }

    val lastOne = launch { // take the result and save to disk
        var counter = 0L
        var frameCounter = 0L
        var timer = 0L
        for ((img, result) in detectResultChannel) {
            val g = img.createGraphics()
            result.forEach { d ->
                val topLeftX = d.centerX - d.width / 2
                val topLeftY = d.centerY - d.height / 2
                g.color = d.className.getColor().toSRGB().let {
                    Color(it.redInt, it.greenInt, it.blueInt, it.alphaInt)
                }
                g.stroke = BasicStroke(3f)
                g.drawRect(topLeftX, topLeftY, d.width, d.height)
                g.font = Font("Default", Font.PLAIN, 20)

                val text = "${d.className}(${"%.2f".format(d.confidence)})"
                val fm = g.fontMetrics
                val textBoundBox = fm.getStringBounds(text, g)
                val textHeight = fm.ascent
                val textY = if (topLeftY - textHeight < 0) topLeftY + d.height + textHeight else topLeftY - textHeight
                // text bg
                g.fillRect(
                    topLeftX, textY,
                    textBoundBox.width.toInt(),
                    textBoundBox.height.toInt()
                )
                g.color = Color.BLACK
                g.drawString(text, topLeftX, textY + textHeight)
            }
            g.dispose()
            val frameNo = ++frameCounter
            ImageIO.write(img, "png", File(outputFolder, "result_$frameNo.png"))
            counter++
            val time = System.currentTimeMillis()
            val dt = time - timer
            if (dt > reportTimer) {
                timer = time
                println("Saver: ${counter * 1000.0 / dt} fps")
                counter = 0
            }
        }
    }


    lastOne.join()
    session.close()
    env.close()
}

fun makeInputFromImage(images: List<BufferedImage>, env: OrtEnvironment): OnnxTensor {
    require(images.all { it.colorModel.colorSpace.type == ColorSpace.TYPE_RGB }) {
        "Require input image in RGB color space"
    }
    // input: [batchSize, 3, h, w], must be one image in each batch
    // dim-2: 0:R, 1:G, 2:B
    // pixel: 0:black, 1:white
    val buffer = FloatBuffer.allocate(images.size * yoloChannel * yoloHeight * yoloWidth)
    images.forEachIndexed { batch, img ->
        val raster = img.raster
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val pixel = raster.getPixel(x, y, null as FloatArray?).let {
                    when (it.size) {
                        3 -> it.toList() // RGB
                        4 -> it.dropLast(1) // RGBA
                        else -> error("Expect RGB or RGBA, but got ${it.size} components")
                    }
                }
                pixel.forEachIndexed { index, v ->
                    buffer.set4d(
                        batch,
                        index, yoloChannel,
                        y, yoloHeight,
                        x, yoloWidth,
                        v / 255
                    )
                }
            }
        }
    }
    return OnnxTensor.createTensor(
        env, buffer,
        longArrayOf(
            images.size.toLong(), yoloChannel.toLong(),
            yoloHeight.toLong(), yoloWidth.toLong()
        )
    )
}


fun FloatBuffer.debugInputImage(batch: Int, outputFile: File) {
    val debugImage = BufferedImage(yoloWidth, yoloHeight, BufferedImage.TYPE_3BYTE_BGR)
    val debugRaster = debugImage.raster
    for (y in 0 until yoloHeight) {
        for (x in 0 until yoloWidth) {
            val d = FloatArray(3) {
                get4d(
                    batch,
                    it, yoloChannel,
                    y, yoloHeight,
                    x, yoloWidth
                ) * 255
            } + 0.0f
            debugRaster.setPixel(x, y, d)
        }
    }
    ImageIO.write(debugImage, "png", outputFile)
}

fun doDetect(
    session: OrtSession, inputTensor: OnnxTensor, names: List<String>
): List<List<Detection>> {
    val input = mapOf(yoloInputName to inputTensor)
    val detections = session.run(input).use { output ->
        (0 until yoloBatchSize).map { processOutput(output, it, names) }
    }
    return detections.map { nms(it, nmsThreshold) }
}

fun setupFFmpeg(video: File): Pair<FFmpegFrameGrabber, Java2DFrameConverter> {
    val grabber = FFmpegFrameGrabber(video)
    grabber.pixelFormat = AV_PIX_FMT_BGR24
    val converter = Java2DFrameConverter()

    return grabber to converter
}

fun nms(input: List<Detection>, threshold: Double): List<Detection> {
    val candidate = LinkedList(input.sortedByDescending { it.confidence })
    val result = mutableListOf<Detection>()
    if (candidate.isEmpty()) return result
    result.add(candidate.removeFirst())
    while (candidate.isNotEmpty()) {
        val c = candidate.removeFirst()
        val maxIoU = result.maxOf { it.calculateIoU(c) }
        if (maxIoU <= threshold) result.add(c)
    }
    return result
}

fun processOutput(result: OrtSession.Result, batch: Int, names: List<String>): List<Detection> {
    require(result.size() == 1) { "More than 1 result" }
    val output = result[0]
    check(output is OnnxTensor) { "Result is not an OnnxTensor" }
    // shape: [batchSize, 84, ...]
    // rows: 0,1,2,3: box x,y,w,h
    // rows: 4..84: 80 labels
    // cols: anchors points, go through everyone and find all
    val (_, labels, anchors) = output.info.shape.map { check(it <= Int.MAX_VALUE); it.toInt() }
    val rawOutput = output.floatBuffer

    val detections = mutableListOf<Detection>()

    for (i in 0 until anchors) {
        // x,y,w,h
        val box = FloatArray(4) { rawOutput.get3d(batch, it, labels, i, anchors) }
        var maxScore = Float.MIN_VALUE
        var maxIndex = -1

        // find the best one for this anchor point
        for (j in 4 until labels) {
            val s = rawOutput.get3d(batch, j, labels, i, anchors)
            if (s >= maxScore) {
                maxScore = s
                maxIndex = j - 4
            }
        }
        // skip if not confidence
        if (maxScore < confidenceThreshold) continue
        val detection = Detection(
            centerX = box[0].toInt(), centerY = box[1].toInt(), width = box[2].toInt(), height = box[3].toInt(),
            confidence = maxScore, classId = maxIndex, className = names[maxIndex]
        )
        detections.add(detection)
    }
    return detections
}

fun setupOrt(
    model: File,
    enableCUDA: Boolean = false,
    allocateAllRAM: Boolean = false
): Pair<OrtEnvironment, OrtSession> {
    val env = OrtEnvironment.getEnvironment()
    val session = env.createSession(
        model.absolutePath,
        SessionOptions().apply {
            if (enableCUDA) addCUDA()
            if (allocateAllRAM) setMemoryPatternOptimization(true)
            setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
            setExecutionMode(SessionOptions.ExecutionMode.PARALLEL)
        }
    )

    return env to session
}
