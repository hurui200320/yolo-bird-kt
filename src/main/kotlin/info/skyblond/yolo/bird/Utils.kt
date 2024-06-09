package info.skyblond.yolo.bird

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.model.Oklch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlin.math.absoluteValue

/**
 * Find all videos with [extension] in [inputFolder],
 * read it, [skip] the frames, and send out using [Channel].
 *
 * Note on [channelCapacity]: with N capacity, the channel will suspend the coroutine
 * on N+1 object, so there are actually N+1 objects in the RAM.
 *
 * If the RAM is tight, use [Channel.RENDEZVOUS] channel, if RAM is still tight,
 * use shorter video clips (no effect on accuracy), or bigger skips (will cause lower accuracy).
 * */
fun CoroutineScope.readVideoFiles(
    inputFolder: File, extension: String, skip: Int,
    channelCapacity: Int
): ReceiveChannel<Pair<File, List<BufferedImage>>> {
    require(channelCapacity >= 0) { "Channel capacity must be greater than 0" }
    val channel = Channel<Pair<File, List<BufferedImage>>>(channelCapacity)
    launch {
        inputFolder.listFiles { it: File ->
            it.isFile && it.extension.lowercase() == extension.lowercase()
        }!!.forEach { file ->
            channel.send(file to grabFrames(file, skip))
        }
        channel.close()
    }
    return channel
}

private val colors: List<Color> by lazy {
    buildList {
        for (i in 0..360 step 20) {
            add(Oklch(0.7, 0.1, i.toDouble()))
        }
    }
}

fun String.getColor(): Color = colors[(hashCode() % colors.size).absoluteValue]

/**
 * Creates a new [File] instance using [this] as parent pathname and a child [pathName] string.
 * */
fun File.subDir(pathName: String) = File(this, pathName).also { it.mkdirs() }

fun CliktCommand.echoInfo(string: String) = echo(terminal.theme.info(string))
fun CliktCommand.echoSuccess(string: String) = echo(terminal.theme.success(string))
fun CliktCommand.echoWarning(string: String) = echo(terminal.theme.warning(string))

typealias FileMetaTime = Triple<FileTime, FileTime, FileTime>

fun File.setMetaTime(meta: FileMetaTime) = this
    .let { Files.getFileAttributeView(this.toPath(), BasicFileAttributeView::class.java) }
    .setTimes(meta.first, meta.second, meta.third)

fun File.getMetaTime(): FileMetaTime = this
    .let { Files.readAttributes(this.toPath(), BasicFileAttributes::class.java) }
    .let { Triple(it.lastModifiedTime(), it.lastAccessTime(), it.creationTime()) }
