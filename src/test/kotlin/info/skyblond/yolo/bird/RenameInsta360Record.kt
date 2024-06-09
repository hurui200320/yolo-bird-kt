package info.skyblond.yolo.bird

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit


/**
 * Rename the Insta360 Ace Pro recording file based on the ctime of the file.
 *
 * The insta360 recording filename looks like this: `VID_20240601_140824_011.mp4`,
 * ideally we want this: `VID_2024-06-01_19-08-14.mp4`.
 *
 * The most complicated solution will be using ffmpeg to read the length of
 * each clip, and calculate from the first one, then renaming.
 *
 * But after checking the file metadata, I found the creation time will
 * perfectly match the starting time of the file.
 * So this script will use the ctime to rename the file.
 *
 * Also, the Insta360 recordings don't have a timestamp in the frame.
 * But unfortunately, there is no way to add it without re-encoding/rendering
 * the hundreds of GB files.
 * */
fun main() {
    val folder = File("D:\\modet\\raw")
    val fileMap = folder.listFiles()!!
        .filter { it.isFile && it.extension.lowercase() == "mp4" }
        .filterNot { it.nameWithoutExtension.contains("-") }
        .groupBy {
            // drop the slice index, group by VID_YYYYMMDD_HHmmSS
            it.nameWithoutExtension.split("_").dropLast(1).joinToString("_")
        }

    fileMap.keys.forEach { prefix ->
        println("Processing $prefix")
        val fileIndexPairs = fileMap[prefix]!!
            .map { it to it.nameWithoutExtension.split("_").last().toLong() }
            // now is file and slice index
            .sortedBy { it.second }
        // here we check if the index starts from 1
        // then verify if the ctime matches the filename
        // this will check if the metadata is correct
        require(fileIndexPairs[0].second == 1L) { "Prefix $prefix didn't start from 1" }
        fileIndexPairs[0].first.checkCreateTimeMatchFilename()
        // everything is ok, renaming
        fileIndexPairs.forEach { (f, index) ->
            val ctime = f.getCreateTime()
            val newName = "${
                f.nameWithoutExtension.split("_").first()
            }_${
                ctime.format(
                    DateTimeFormatterBuilder()
                        .appendValue(ChronoField.YEAR, 4)
                        .appendLiteral("-")
                        .appendValue(ChronoField.MONTH_OF_YEAR, 2)
                        .appendLiteral("-")
                        .appendValue(ChronoField.DAY_OF_MONTH, 2)
                        .toFormatter()
                )
            }_${
                ctime.format(
                    DateTimeFormatterBuilder()
                        .appendValue(ChronoField.HOUR_OF_DAY, 2)
                        .appendLiteral("-")
                        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                        .appendLiteral("-")
                        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                        .toFormatter()
                )
            }.${f.extension}"
            val newFile = File(folder, newName)
            val metaTime = f.getMetaTime()
            println("Moving index #$index")
            Files.move(f.toPath(), newFile.toPath())
            newFile.setMetaTime(metaTime)
            println("Moved $f to $newFile")
        }

    }

}

private fun File.checkCreateTimeMatchFilename() {
    val startTime = this.parseTimeFromFilename()
    val createTime = this.getCreateTime()
    require(startTime == createTime) {
        "File `$this` created at $createTime " +
                "but filename suggests it starts at $startTime"
    }
}

private fun File.getCreateTime(): LocalDateTime {
    val attr = Files.readAttributes(this.toPath(), BasicFileAttributes::class.java)
    return attr.creationTime().toInstant()
        .truncatedTo(ChronoUnit.SECONDS)
        .atZone(ZoneId.systemDefault()).toLocalDateTime()
}

private fun File.parseTimeFromFilename(): LocalDateTime = this
    .nameWithoutExtension
    .split("_")
    .let {
        LocalDateTime.parse(
            it[1] + it[2], // 20240601140824
            DateTimeFormatterBuilder()
                .appendValue(ChronoField.YEAR, 4)
                .appendValue(ChronoField.MONTH_OF_YEAR, 2)
                .appendValue(ChronoField.DAY_OF_MONTH, 2)
                .appendValue(ChronoField.HOUR_OF_DAY, 2)
                .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                .toFormatter()
        )
    }
