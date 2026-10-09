package hackman.trevor.collatz.ui

import java.io.StringWriter
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Writes uncaught UI failures under `data/crashes`.
 * The directory is the project `data/crashes` folder, found by walking up from the working directory.
 */
object CrashLog {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss.SSS")
    @Volatile private var directory: Path? = null
    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install() {
        val crashes = projectDataDirectory().resolve("crashes")
        crashes.createDirectories()
        directory = crashes
        if (Thread.getDefaultUncaughtExceptionHandler() !is Handler) {
            previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(Handler)
        }
        System.setProperty("sun.awt.exception.handler", AwtHandler::class.java.name)
    }

    fun record(thread: Thread, error: Throwable): Path? {
        val crashes = directory ?: return null
        val file = crashes.resolve("${LocalDateTime.now().format(stamp)}-${error.javaClass.simpleName}.txt")
        val trace = StringWriter()
        error.printStackTrace(PrintWriter(trace))
        file.writeText(
            "time: ${LocalDateTime.now()}\nthread: ${thread.name}\n\n$trace",
        )
        return file
    }

    private fun projectDataDirectory(): Path {
        var dir = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (true) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))) return dir.resolve("data")
            dir = dir.parent ?: return Path.of("data")
        }
    }

    private object Handler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            record(thread, error)
            previous?.uncaughtException(thread, error)
        }
    }
}

/** Swing reports UI-thread failures here instead of [Thread.UncaughtExceptionHandler]. */
class AwtHandler {
    @Suppress("unused")
    fun handle(error: Throwable) {
        CrashLog.record(Thread.currentThread(), error)
    }
}
