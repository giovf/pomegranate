package com.gankdat.pomegranate.transfer

import com.gankdat.pomegranate.data.model.FolderStrategy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** `<root>/Pomegranate/YYYY/YYYY-MM/<Origin>/` folder naming; pure. */
internal object DestinationLayout {
    const val ROOT_FOLDER: String = "Pomegranate"
    private val year = DateTimeFormatter.ofPattern("yyyy")
    private val month = DateTimeFormatter.ofPattern("yyyy-MM")
    private val unsafe = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    /** Folder names from the root downwards. An unknown origin means the file stays in the month folder. */
    fun folders(dateTakenMillis: Long, origin: String?, strategy: FolderStrategy, zone: ZoneId = ZoneId.systemDefault()): List<String> {
        val date = Instant.ofEpochMilli(if (dateTakenMillis > 0) dateTakenMillis else 0L).atZone(zone)
        val base = listOf(ROOT_FOLDER, year.format(date), month.format(date))
        val originFolder = origin?.let(::safeName)?.takeIf { it.isNotEmpty() && strategy == FolderStrategy.BY_ORIGIN }
        return if (originFolder != null) base + originFolder else base
    }

    /** Strips characters document providers reject and trims to a sane length. */
    fun safeName(name: String): String = name.replace(unsafe, "_").trim().trim('.').take(120)

    /** `IMG_1.jpg` → `IMG_1 (1).jpg`, `IMG_1 (2).jpg`… until [exists] says no. */
    fun uniqueName(filename: String, exists: (String) -> Boolean): String {
        val safe = safeName(filename).ifEmpty { "file" }
        if (!exists(safe)) return safe
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var n = 1
        while (true) {
            val candidate = "$stem ($n)$ext"
            if (!exists(candidate)) return candidate
            n++
        }
    }
}
