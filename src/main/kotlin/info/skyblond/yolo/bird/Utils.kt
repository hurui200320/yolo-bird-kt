package info.skyblond.yolo.bird

import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.model.Oklch
import java.awt.image.BufferedImage
import java.io.File
import java.nio.FloatBuffer
import javax.imageio.ImageIO
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

fun loadNames(path: String): List<String> =
    File(path).readLines().filter { it.isNotBlank() }
        .also { check(it.size == 80) { "Expecting 80 names, but got ${it.size}" } }

fun FloatBuffer.set4d(
    batch: Int,
    channel: Int, channelSize: Int,
    y: Int, height: Int,
    x: Int, width: Int,
    value: Float
) {
    val idx = batch * width * height * channelSize + channel * width * height + y * width + x
    put(idx, value)
}

fun FloatBuffer.get4d(
    batch: Int,
    channel: Int, channelSize: Int,
    y: Int, height: Int,
    x: Int, width: Int
): Float {
    val idx = batch * width * height * channelSize + channel * width * height + y * width + x
    return get(idx)
}

fun FloatBuffer.set3d(
    channel: Int,
    y: Int, height: Int,
    x: Int, width: Int,
    value: Float
) {
    val idx = channel * width * height + y * width + x
    put(idx, value)
}

fun FloatBuffer.get3d(
    channel: Int,
    y: Int, height: Int,
    x: Int, width: Int
): Float {
    val idx = channel * width * height + y * width + x
    return get(idx)
}

data class Detection(
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    val height: Int,
    val confidence: Float,
    val classId: Int,
    val className: String
) {
    /**
     * Calculate intersection area, return all 0 if not intersect.
     *
     * @return the intersection area.
     * */
    private fun intersectionArea(other: Detection): Double {
        val ourX1 = centerX - width / 2.0
        val ourY1 = centerY - height / 2.0
        val ourX2 = ourX1 + width
        val ourY2 = ourY1 + height

        val otherX1 = other.centerX - other.width / 2.0
        val otherY1 = other.centerY - other.height / 2.0
        val otherX2 = otherX1 + other.width
        val otherY2 = otherY1 + other.height

        // use the leftest x2 minus rightest x1, clamp to 0
        val width = (min(ourX2, otherX2) - max(ourX1, otherX1)).coerceAtLeast(0.0)
        // use the lowest y2 minus the highest y1, clamp to 0
        val height = (min(ourY2, otherY2) - max(ourY1, otherY1))
        return width * height
    }

    /**
     * IoU = intersection area / union area,
     * where union area = our area + other area - intersection area
     * */
    fun calculateIoU(other: Detection): Double {
        val ourArea = width.toDouble() * height
        val otherArea = other.width.toDouble() * height
        val intersection = intersectionArea(other)
        val unionArea = ourArea + otherArea - intersection
        return intersection / unionArea
    }
}

private val colors: List<Color> by lazy {
    buildList {
        for (i in 0..360 step 10) {
            add(Oklch(0.7, 0.1, i.toDouble()))
        }
    }
}

fun String.getColor(): Color = colors[(hashCode() % colors.size).absoluteValue]
