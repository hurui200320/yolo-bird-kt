package info.skyblond.yolo.bird

import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.opencv.global.opencv_core.*
import org.bytedeco.opencv.global.opencv_dnn.*
import org.bytedeco.opencv.global.opencv_highgui.imshow
import org.bytedeco.opencv.global.opencv_highgui.waitKey
import org.bytedeco.opencv.global.opencv_imgproc.LINE_8
import org.bytedeco.opencv.global.opencv_imgproc.rectangle
import org.bytedeco.opencv.opencv_core.*
import org.bytedeco.opencv.opencv_dnn.Net
import org.bytedeco.opencv.opencv_text.FloatVector
import org.bytedeco.opencv.opencv_text.IntVector
import java.io.File


private const val yoloWidth = 1920
private const val yoloHeight = 1088
private const val confidenceThreshold = 0.3f
private const val nmsThreshold = 0.4f


fun main() {
    val net = readNetFromONNX("D:\\code\\PycharmProjects\\yolov8\\yolov8x.onnx")
    val names = File("coco.names").readLines().filter { it.isNotBlank() }
    check(names.size == 80)
    // enable cuda backend if available
//    if (getCudaEnabledDeviceCount() > 0) {
//        net.setPreferableBackend(DNN_BACKEND_CUDA)
//        net.setPreferableTarget(DNN_TARGET_CUDA)
//    }

    val grabber = FFmpegFrameGrabber(File("D:\\modet\\morning_2024-05-14_08-55_0_0.mp4"))
    grabber.pixelFormat = AV_PIX_FMT_BGR24
    grabber.start()

    val converter = OpenCVFrameConverter.ToMat()

    while (grabber.hasVideo()) {
        val ffmpegFrame = grabber.grabImage()
        val image = converter.convertToMat(ffmpegFrame)
        predict(net, names, image)
    }

    grabber.stop()
}

fun predict(net: Net, names: List<String>, image: Mat) {
    // image: BGR, [h, w]

    // transfer the image into 4 dim blob
    // each pixel = (input - mean) * scale
    // in the end: pixel: 0 -> black, 1 -> white
    // [batch, channel(RGB), h, w]
    val blob = blobFromImage(
        image,
        1 / 255.0,
        Size(yoloWidth, yoloHeight),
        Scalar(0.0),
        true, false, CV_32F
    )
//    println(blob.shape())

    // the output shape: [batch, 84 (80 labels + box(x,y,w,h)), ???]
    val outputs = MatVector()
    net.setInput(blob)
    net.forward(outputs, net.unconnectedOutLayersNames)
    check(outputs.size() == 1L) { "More than 1 output in result" }
    val output = outputs[0]
//    println(output.shape())
    val (batchSize, outputLabelSize, outputPointSize) = output.shape()
    check(batchSize == 1) {"More than 1 img in batch"}
    // we check the points one by one to see if there is anything detected
    val data = output.createIndexer<FloatIndexer>()

    val xFactor = image.cols() / yoloWidth.toFloat()
    val yFactor = image.rows() / yoloHeight.toFloat()

    val detectedClassId = IntVector()
    val detectedConfidences = FloatVector()
    val detectedBox = RectVector()
    val detectedCenterPoint = mutableListOf<Pair<Float, Float>>()

    for (pIndex in 0 until outputPointSize) {
        // 0:x 1:y 2:w 3:h
        val box = FloatArray(4) { data.get(0, it.toLong(), pIndex.toLong()) }
        var maxScore = Float.MIN_VALUE
        var maxIndex = -1
        for (i in 4 until outputLabelSize) {
            val score: Float = data.get(0, i.toLong(), pIndex.toLong())
            if (score > maxScore) {
                maxScore = score
                maxIndex = i - 4
            }
        }

        if (maxScore > confidenceThreshold) {
            // we detected something
            println(box.toList())
            // TODO: Why it's all zero when using cuda?

            detectedConfidences.push_back(maxScore)
            detectedClassId.push_back(maxIndex)

            val (x, y, w, h) = box
            val left = ((x - 0.5 * w) * xFactor).toInt()
            val top = ((y - 0.5 * h) * yFactor).toInt()
            val width = (w * xFactor).toInt()
            val height = (h * yFactor).toInt()
            detectedCenterPoint.addLast(
                (x * xFactor) to (y * yFactor)
            )
            detectedBox.push_back(Rect(left, top, width, height))
        }
    }

    //    std::vector<int> nms_result;
//    val nmsResult = IntPointer(detectedConfidences.size())
//    val confidencesPointer = FloatPointer(detectedConfidences.size())
//    confidencesPointer.put(*detectedConfidences.get())

//    NMSBoxes(detectedBox, confidencesPointer, confidenceThreshold, nmsThreshold, nmsResult, 1f, 0)

    //    std::vector<Detection> detections{};
//    for (i in 0 until nmsResult.limit()) {
//        val idx = nmsResult[i]
    for (i in 0 until detectedClassId.limit()) {
        val idx = i
        val classId = detectedClassId[idx.toLong()]
        val confidence = detectedConfidences[idx.toLong()]
        val name = names[classId]
        val box = detectedBox[idx.toLong()]
//        val point = detectedCenterPoint[idx]
        println("Detected: $name with confidence $confidence")
        //        detections.push_back(result);
        rectangle(
            image, box, Scalar.MAGENTA, 2, LINE_8, 0
        )
    }
    imshow("yolo", image)
    waitKey()
    // TODO free/release resources from c side

    //    return detections;

}
