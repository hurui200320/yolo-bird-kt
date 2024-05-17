package info.skyblond.yolo.bird

import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.javacpp.indexer.UByteIndexer
import org.bytedeco.opencv.opencv_core.Mat
import java.awt.image.BufferedImage


fun Mat.shape() = IntArray(dims()) { size(it) }.toList()

fun Mat.toImage2D(): BufferedImage {
    require(dims() == 2) { "Not a 2D mat" }
    val (w, h) = shape()
    val indexer = createIndexer<UByteIndexer>()

    val javaImage = BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR)
    val raster = javaImage.raster
    for (y in 0 until h) {
        for (x in 0 until w) {
            val b = indexer[x.toLong(), y.toLong(), 0]
            val g = indexer[x.toLong(), y.toLong(), 1]
            val r = indexer[x.toLong(), y.toLong(), 2]
            raster.setPixel(x, y, intArrayOf(r, g, b))
        }
    }

    return javaImage
}

fun Mat.blobToBufferedImage(): List<BufferedImage> {
    require(dims() == 4) { "Not a 4D blob mat" }
    val blobIndexer = createIndexer<FloatIndexer>()
    println(blobIndexer.sizes().toList())
    val batchSize = size(0)
    val width = size(3)
    val height = size(2)
    return (0 until batchSize).map { i ->
        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
        val raster = bufferedImage.raster
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = blobIndexer.get(i.toLong(), 0, y.toLong(), x.toLong(), 0)
                val g = blobIndexer.get(i.toLong(), 1, y.toLong(), x.toLong(), 0)
                val b = blobIndexer.get(i.toLong(), 2, y.toLong(), x.toLong(), 0)
                raster.setPixel(x, y, floatArrayOf(r * 255, g * 255, b * 255))
            }
        }
        bufferedImage
    }
}
