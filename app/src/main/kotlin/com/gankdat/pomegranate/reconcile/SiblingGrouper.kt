package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.entity.MediaGroupKind
import java.util.Locale

/** A file from either side, identified by an opaque [key] (entity id plus side). */
internal data class SiblingFile(val key: Any, val filename: String)

internal data class SiblingGroup(val kind: MediaGroupKind, val members: List<SiblingFile>)

/**
 * Finds files that belong to one capture: RAW+JPEG, Live Photo halves,
 * motion-photo sidecars and original/edited pairs. Pure and order-stable;
 * each file joins at most one group, with the rules applied in priority order.
 */
internal object SiblingGrouper {
    private val rawExtensions = setOf("dng", "arw", "cr2", "cr3", "nef", "orf", "rw2", "raf", "srw", "pef")
    private val jpegExtensions = setOf("jpg", "jpeg", "heic", "heif")
    private val liveStillExtensions = setOf("heic", "heif", "jpg", "jpeg")
    private val liveVideoExtensions = setOf("mov", "mp4")
    private val editedSuffixes = listOf("_original", "-original", "_edited", "-edited", "~2", "(1)", " (1)", "-edit", "_edit")
    private val editorPrefixes = listOf("vsco_", "snapseed_", "lr_", "lightroom_")

    fun group(files: List<SiblingFile>): List<SiblingGroup> {
        val taken = HashSet<Any>()
        val groups = ArrayList<SiblingGroup>()
        val byBase = files.groupBy { base(it.filename) }

        fun take(kind: MediaGroupKind, members: List<SiblingFile>) {
            if (members.size < 2 || members.any { it.key in taken }) return
            taken.addAll(members.map { it.key })
            groups += SiblingGroup(kind, members)
        }

        // 1. RAW + JPEG with the same base name.
        for ((_, members) in byBase) {
            val raw = members.filter { ext(it.filename) in rawExtensions }
            val jpeg = members.filter { ext(it.filename) in jpegExtensions }
            if (raw.size == 1 && jpeg.size == 1) take(MediaGroupKind.RAW_PAIR, listOf(jpeg.single(), raw.single()))
        }
        // 2. Motion photos: Pixel/Samsung ".MP.jpg" sidecars or "MVIMG_" stills with a same-name video.
        for ((_, members) in byBase) {
            val still = members.filter { isMotionStill(it.filename) }
            val video = members.filter { ext(it.filename) in liveVideoExtensions }
            if (still.size == 1 && video.size == 1) take(MediaGroupKind.MOTION_PHOTO, listOf(still.single(), video.single()))
        }
        // 3. Live Photos: HEIC/JPG still plus MOV/MP4 clip with the same base name.
        for ((_, members) in byBase) {
            val still = members.filter { ext(it.filename) in liveStillExtensions }
            val video = members.filter { ext(it.filename) in liveVideoExtensions }
            if (still.size == 1 && video.size == 1) take(MediaGroupKind.LIVE_PHOTO, listOf(still.single(), video.single()))
        }
        // 4. Original + edited: suffix conventions or editor prefixes pointing at an existing base.
        val byBaseLoose = files.groupBy { editedBase(it.filename) }
        for ((editedBase, members) in byBaseLoose) {
            val originals = byBase[editedBase].orEmpty().filter { editedBase(it.filename) == editedBase && !isEditedName(it.filename) }
            val edits = members.filter { isEditedName(it.filename) }
            if (originals.size == 1 && edits.size == 1) take(MediaGroupKind.EDIT_PAIR, listOf(originals.single(), edits.single()))
        }
        return groups
    }

    fun ext(filename: String): String = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)

    /** Name without extension, lowercased, with motion-photo markers removed so `PXL_1.MP.jpg` pairs with `PXL_1.mp4`. */
    fun base(filename: String): String {
        var b = filename.substringBeforeLast('.').lowercase(Locale.ROOT)
        if (b.endsWith(".mp")) b = b.removeSuffix(".mp")
        if (b.startsWith("mvimg_")) b = "img_" + b.removePrefix("mvimg_")
        return b
    }

    private fun isMotionStill(filename: String): Boolean {
        val lower = filename.lowercase(Locale.ROOT)
        return ext(lower) in jpegExtensions && (lower.contains(".mp.") || lower.startsWith("mvimg_"))
    }

    private fun isEditedName(filename: String): Boolean {
        val b = base(filename)
        return editedSuffixes.any { b.endsWith(it) } || editorPrefixes.any { b.startsWith(it) }
    }

    /** The base an edited name points back to; the name itself when it carries no edit marker. */
    private fun editedBase(filename: String): String {
        var b = base(filename)
        editorPrefixes.firstOrNull { b.startsWith(it) }?.let { b = b.removePrefix(it) }
        editedSuffixes.firstOrNull { b.endsWith(it) }?.let { b = b.removeSuffix(it).trimEnd() }
        return b
    }
}
