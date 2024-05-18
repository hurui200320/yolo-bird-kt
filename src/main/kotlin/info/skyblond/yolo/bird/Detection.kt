package info.skyblond.yolo.bird

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

data class Detection(
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    val height: Int,
    val confidence: Float,
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

    fun drawOn(
        image: BufferedImage,
        boxStroke: Float = 2f,
        font: Font = Font("Default", Font.PLAIN, 20),
        label: Detection.() -> String = { "$className ${(confidence * 100).toInt()}%" }
    ) {
        val g = image.createGraphics()

        val topLeftX = centerX - width / 2
        val topLeftY = centerY - height / 2
        g.color = className.getColor().toSRGB().let {
            Color(it.redInt, it.greenInt, it.blueInt, it.alphaInt)
        }
        g.stroke = BasicStroke(boxStroke)
        g.drawRect(topLeftX, topLeftY, width, height)
        g.font = font

        val text = this.label()
        val fm = g.fontMetrics
        val textBoundBox = fm.getStringBounds(text, g)
        val textHeight = fm.ascent
        val textY = if (topLeftY - textHeight < 0) topLeftY + height + textHeight else topLeftY - textHeight
        // text bg
        g.fillRect(
            topLeftX, textY,
            textBoundBox.width.toInt(),
            textBoundBox.height.toInt()
        )
        g.color = Color.BLACK
        g.drawString(text, topLeftX, textY + textHeight)

        g.dispose()
    }
}
