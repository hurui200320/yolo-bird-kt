package info.skyblond.yolo.bird

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.awt.AlphaComposite
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.io.File
import java.nio.FloatBuffer
import java.util.*


/**
 * Wrapper of a ONNX based YOLOv8 network.
 * */
class YOLOv8(
    private val ortEnv: OrtEnvironment,
    private val labels: List<String>,
    model: File, sessionSetup: SessionOptions.() -> Unit
) : AutoCloseable {
    companion object {
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
    }

    private val session: OrtSession = ortEnv.createSession(
        model.absolutePath,
        SessionOptions().apply(sessionSetup)
    )

    private val inputName: String
    private val inputShape: LongArray

    val batchSize get() = inputShape[0]
    val batchSizeInt get() = batchSize.toIntSafe()

    val inputChannel get() = inputShape[1]
    val inputHeight get() = inputShape[2]
    val inputWidth get() = inputShape[3]


    private val outputShape: LongArray

    val outputLabelSize get() = outputShape[1]
    private val outputLabelSizeInt get() = outputLabelSize.toIntSafe()
    private val outputAnchorSize get() = outputShape[2]

    init {
        // check input shape        
        val inputInfo = session.inputInfo
        require(inputInfo.size == 1) { "Expect 1 input tensor, but got ${inputInfo.size}" }
        val input = inputInfo.values.first()
        inputName = input.name
        // the input must be a tensor: [batch, channel, height, width]
        inputShape = (input.info as TensorInfo).shape
        require(inputShape.size == 4) { "Expect input is 4 dimension, but got ${inputShape.size}" }
        require(inputChannel == 3L) { "Expect input has 3 channels, but got $inputChannel" }

        // check output size: [batch, 4 + labels, anchors]
        val outputInfo = session.outputInfo
        require(outputInfo.size == 1) { "Expect 1 output tensor, but got ${outputInfo.size}" }
        val output = outputInfo.values.first()
        outputShape = (output.info as TensorInfo).shape
        require(outputLabelSizeInt - 4 == labels.size) {
            "Model has ${outputLabelSizeInt - 4} labels, but name file only gives ${labels.size}"
        }
    }

    /**
     * Do inference (detection) on any number of [images]
     * */
    fun doInference(
        images: List<BufferedImage>,
        inferenceParameter: InferenceParameter,
        afterBatchFinish: (List<List<Detection>>) -> Unit = {}
    ): List<List<Detection>> = runBlocking(Dispatchers.Default) {
        images.chunked(batchSizeInt).map { b ->
            async { makeInputFromImage(b.map { ensureRGB(it) }) to b.size }
        }.map { r ->
            val (tensor, size) = r.await()
            doBatch(tensor, size, inferenceParameter)
                .also { tensor.close() }
                .also { afterBatchFinish(it) }
        }.flatten()
    }

    /**
     * Do one batch, the size of [images] must not bigger than [batchSize].
     * Otherwise, the onnx model will throw error.
     *
     *
     * */
    private fun doBatch(
        inputTensor: OnnxTensor, size: Int,
        inferenceParameter: InferenceParameter
    ): List<List<Detection>> {
        val input = mapOf(inputName to inputTensor)
        val detections = session.run(input).use { output ->
            // it should be onnx tensor
            val outputTensor = output.first().value as OnnxTensor
            runBlocking(Dispatchers.Default) {
                // don't take the padding one
                (0 until size).map {
                    async {
                        decodeResult(outputTensor, it, inferenceParameter)
                    }
                }.awaitAll()
            }
        }
        return detections.map { nms(it, inferenceParameter.nmsThreshold) }
    }

    /**
     * Decode the [batch]-th result from the model output
     * */
    private fun decodeResult(output: OnnxTensor, batch: Int, inferenceParameter: InferenceParameter): List<Detection> {
        // shape: [batchSize, 84, ...]
        // rows: 0,1,2,3: box x,y,w,h
        // rows: 4..84: 80 labels
        // cols: anchors points, go through everyone and find all
        val rawOutput = output.floatBuffer

        val detections = mutableListOf<Detection>()

        // for each anchor, find the best prediction class
        for (i in 0 until outputAnchorSize) {
            // box: x,y,w,h
            val box = FloatArray(4) { rawOutput[batch, it, i] }
            var maxScore = Float.MIN_VALUE
            var maxIndex = 0 // index safe default value
            // find the best one for this anchor point
            for (j in 4 until outputLabelSizeInt) {
                val s = rawOutput[batch, j, i]
                if (s >= maxScore) {
                    maxScore = s
                    maxIndex = j - 4
                }
            }
            // skip if not confidence
            if (maxScore < inferenceParameter.getThreshold(labels[maxIndex])) continue
            val detection = Detection(
                centerX = box[0].toInt(), centerY = box[1].toInt(),
                width = box[2].toInt(), height = box[3].toInt(),
                className = labels[maxIndex], confidence = maxScore,
            )
            detections.add(detection)
        }
        return detections
    }

    private operator fun FloatBuffer.get(
        batch: Int, label: Int, anchor: Long
    ): Float {
        val idx = outputAnchorSize * (batch * outputLabelSize + label) + anchor
        return get(idx.toIntSafe())
    }

    /**
     * Apply NMS filter to the [input], return the selected [Detection].
     * */
    private fun nms(input: List<Detection>, threshold: Double): List<Detection> {
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

    /**
     * The returned image will be guaranteed to be in RGB color space
     * */
    private fun ensureRGB(image: BufferedImage): BufferedImage {
        if (image.colorModel.colorSpace.type == ColorSpace.TYPE_RGB) return image
        val result = BufferedImage(image.width, image.height, image.type)
        val g = result.createGraphics()
        try {
            g.composite = AlphaComposite.Src
            g.drawImage(image, 0, 0, null)
        } finally {
            g.dispose()
        }
        return result
    }

    /**
     * Turn input images into one input tensor.
     * The input image must in RGB color space, otherwise the prediction might be wrong.
     * The input size must not bigger than [batchSize].
     * If smaller, black image will be use as padding.
     * */
    private fun makeInputFromImage(images: List<BufferedImage>): OnnxTensor {
        require(images.size <= batchSize) { "Too many input images. Batch size: $batchSize, got ${images.size}" }
        // input: [batchSize, 3, h, w], must be one image in each batch
        // dim-2: 0:R, 1:G, 2:B
        // pixel: 0:black, 1:white
        val buffer = FloatBuffer.allocate(
            (batchSize * inputChannel * inputHeight * inputWidth).toIntSafe()
        )
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
                    pixel.forEachIndexed { c, v ->
                        buffer[batch, c, y, x] = v / 255
                    }
                }
            }
        }
        return OnnxTensor.createTensor(ortEnv, buffer, inputShape)
    }

    /**
     * Set [FloatBuffer] using index
     * */
    private operator fun FloatBuffer.set(
        batch: Int, channel: Int, y: Int, x: Int,
        value: Float
    ) {
        val area = inputHeight * inputWidth
        val idx = batch * area * inputChannel + channel * area + y * inputWidth + x
        put(idx.toIntSafe(), value)
    }

    /**
     * Convert [Long] to [Int], but will throw error if the value overflowed.
     * */
    private fun Long.toIntSafe(): Int {
        check(this <= Int.MAX_VALUE) { "Long value can't be int: overflow!" }
        return this.toInt()
    }

    override fun close() {
        session.close()
    }
}
