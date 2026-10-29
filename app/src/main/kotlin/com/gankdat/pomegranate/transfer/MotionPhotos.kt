package com.gankdat.pomegranate.transfer

import java.io.InputStream
import java.io.OutputStream

/** What a pulled photo needs so a motion photo keeps its motion. */
internal enum class MotionPlan {
    /** An ordinary photo: save the still. */
    NOT_MOTION,

    /** Google already served a complete motion photo (still plus embedded clip): save as is. */
    ALREADY_MOTION,

    /** A motion photo whose clip must be fetched separately and joined to the JPEG still. */
    FETCH_CLIP,

    /** A motion photo that cannot be rebuilt here (not a JPEG): save the still and say so. */
    STILL_ONLY,
}

/**
 * Recognises motion photos and the clips Google serves for them. Detection
 * is deliberately narrow so ordinary photos never cost an extra request:
 * Pixel/legacy file names, or the motion markers camera apps write into XMP.
 */
internal object MotionPhotos {
    /** Bytes of the still worth scanning for XMP; APP1 segments sit at the start of the file. */
    const val HEAD_BYTES: Int = 256 * 1024

    private val motionName = Regex("""(\.MP\.(jpe?g|heic|heif)$)|(^MVIMG_)""", RegexOption.IGNORE_CASE)
    private val motionXmp = Regex("""G?Camera:(MotionPhoto|MicroVideo)\s*(=\s*["']1["']|>\s*1\s*<)""")
    private val containerItem = Regex("""<Container:Item\b[^>]*>""")
    private val itemLength = Regex("""Item:Length\s*=\s*["'](\d+)["']""")
    private val microVideoOffset = Regex("""GCamera:MicroVideoOffset\s*=\s*["'](\d+)["']""")

    fun nameSuggestsMotion(filename: String): Boolean = motionName.containsMatchIn(filename)

    /** True when the start of the file declares itself a motion photo (works for JPEG and HEIC XMP alike). */
    fun xmpSaysMotion(head: ByteArray): Boolean = motionXmp.containsMatchIn(latin1(head, HEAD_BYTES))

    fun plan(filename: String, still: ByteArray): MotionPlan {
        if (!nameSuggestsMotion(filename) && !xmpSaysMotion(still)) return MotionPlan.NOT_MOTION
        if (!isJpeg(still)) return MotionPlan.STILL_ONLY
        return if (embeddedVideoLength(still) != null) MotionPlan.ALREADY_MOTION else MotionPlan.FETCH_CLIP
    }

    fun isJpeg(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

    /** An ISO base media file (MP4/MOV) starts with a `ftyp` box. */
    fun isMp4(head: ByteArray): Boolean = head.size >= 12 && latin1(head.copyOfRange(4, 8), 4) == "ftyp"

    /** QuickTime brand means `.mov`; anything else in an ISO media file is treated as MP4. */
    fun clipMime(head: ByteArray): String = if (head.size >= 12 && latin1(head.copyOfRange(8, 12), 4) == "qt  ") "video/quicktime" else "video/mp4"

    /** Length of a clip already embedded at the end of [jpeg], when its XMP points at one that really is there. */
    fun embeddedVideoLength(jpeg: ByteArray): Long? {
        val text = latin1(jpeg, HEAD_BYTES)
        val candidates = buildList {
            containerItem.findAll(text).filter { "MotionPhoto" in it.value }.forEach { item ->
                itemLength.find(item.value)?.groupValues?.get(1)?.toLongOrNull()?.let(::add)
            }
            microVideoOffset.findAll(text).forEach { m -> m.groupValues[1].toLongOrNull()?.let(::add) }
        }
        return candidates.firstOrNull { length ->
            val start = jpeg.size - length
            length > 0 && start > 0 && start + 12 <= jpeg.size && isMp4(jpeg.copyOfRange(start.toInt(), start.toInt() + 12))
        }
    }

    private fun latin1(bytes: ByteArray, limit: Int): String = String(bytes, 0, minOf(bytes.size, limit), Charsets.ISO_8859_1)
}

/**
 * Writes a still and its clip as one file in Google's Motion Photo format:
 * the JPEG with an XMP directory describing both parts, followed by the clip.
 * Existing EXIF and other segments are kept; any previous XMP is replaced
 * (it may describe a different layout) and anything after the JPEG's end
 * marker is dropped. The legacy MicroVideo fields are written too, for
 * gallery apps that only read those.
 */
internal object MotionPhotoWriter {
    private const val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000"
    private const val XMP_EXTENSION_HEADER = "http://ns.adobe.com/xmp/extension/\u0000"
    private const val EXIF_HEADER = "Exif\u0000\u0000"
    private const val MAX_SEGMENT_LENGTH = 0xFFFF

    /** Returns the number of bytes written. Throws [IllegalArgumentException] when [still] is not a well-formed JPEG. */
    fun write(still: ByteArray, clip: InputStream, clipLength: Long, clipMime: String, sink: OutputStream): Long {
        require(MotionPhotos.isJpeg(still)) { "Not a JPEG" }
        require(clipLength > 0) { "Empty clip" }
        val layout = parse(still)
        val kept = layout.segments.filterNot { isApp1With(still, it, XMP_HEADER) || isApp1With(still, it, XMP_EXTENSION_HEADER) }
        val insertAt = kept.indexOfFirst { marker(still, it) != 0xE0 && !isApp1With(still, it, EXIF_HEADER) }.let { if (it < 0) kept.size else it }
        val xmp = xmpSegment(clipLength, clipMime)

        var written = 0L
        fun out(bytes: ByteArray, from: Int = 0, to: Int = bytes.size) {
            sink.write(bytes, from, to - from)
            written += to - from
        }
        out(still, 0, 2) // SOI
        kept.forEachIndexed { i, seg ->
            if (i == insertAt) out(xmp)
            out(still, seg.first, seg.last + 1)
        }
        if (insertAt == kept.size) out(xmp)
        out(still, layout.imageStart, layout.imageEnd)
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val read = clip.read(buffer)
            if (read < 0) break
            out(buffer, 0, read)
            copied += read
        }
        require(copied == clipLength) { "Clip length changed: expected $clipLength, read $copied" }
        sink.flush()
        return written
    }

    /** Header segments (each as an inclusive byte range) and the image data from the first SOS through EOI. */
    private class Layout(val segments: List<IntRange>, val imageStart: Int, val imageEnd: Int)

    private fun parse(jpeg: ByteArray): Layout {
        val segments = mutableListOf<IntRange>()
        var pos = 2
        while (true) {
            require(pos + 4 <= jpeg.size && jpeg[pos] == 0xFF.toByte()) { "Malformed JPEG header" }
            val marker = jpeg[pos + 1].toInt() and 0xFF
            if (marker == 0xDA) break // start of scan: the rest is image data
            require(marker != 0xD9) { "JPEG has no image data" }
            if (marker == 0xFF) { pos++; continue } // fill byte
            val length = ((jpeg[pos + 2].toInt() and 0xFF) shl 8) or (jpeg[pos + 3].toInt() and 0xFF)
            require(length >= 2 && pos + 2 + length <= jpeg.size) { "Malformed JPEG segment" }
            segments += pos until pos + 2 + length
            pos += 2 + length
        }
        return Layout(segments, pos, endOfImage(jpeg, pos))
    }

    /** Index just past the EOI marker. Entropy-coded data escapes 0xFF, so the first FF D9 after SOS is the end. */
    private fun endOfImage(jpeg: ByteArray, from: Int): Int {
        var i = from
        while (i + 1 < jpeg.size) {
            if (jpeg[i] == 0xFF.toByte() && jpeg[i + 1] == 0xD9.toByte()) return i + 2
            i++
        }
        throw IllegalArgumentException("JPEG has no end marker")
    }

    private fun marker(jpeg: ByteArray, seg: IntRange): Int = jpeg[seg.first + 1].toInt() and 0xFF

    private fun isApp1With(jpeg: ByteArray, seg: IntRange, header: String): Boolean {
        if (marker(jpeg, seg) != 0xE1) return false
        val start = seg.first + 4
        val end = start + header.length
        return end <= seg.last + 1 && String(jpeg, start, header.length, Charsets.ISO_8859_1) == header
    }

    private fun xmpSegment(clipLength: Long, clipMime: String): ByteArray {
        val payload = (XMP_HEADER + xmp(clipLength, clipMime)).toByteArray(Charsets.UTF_8)
        val length = payload.size + 2
        check(length <= MAX_SEGMENT_LENGTH) { "XMP too large" }
        return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }

    /** Motion Photo format 1.0 directory plus the legacy MicroVideo fields; the presentation time is unknown (-1). */
    fun xmp(clipLength: Long, clipMime: String): String = """<?xpacket begin="${"\uFEFF"}" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
    xmlns:Container="http://ns.google.com/photos/1.0/container/"
    xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
    GCamera:MotionPhoto="1"
    GCamera:MotionPhotoVersion="1"
    GCamera:MotionPhotoPresentationTimestampUs="-1"
    GCamera:MicroVideo="1"
    GCamera:MicroVideoVersion="1"
    GCamera:MicroVideoOffset="$clipLength"
    GCamera:MicroVideoPresentationTimestampUs="-1">
   <Container:Directory>
    <rdf:Seq>
     <rdf:li rdf:parseType="Resource">
      <Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/>
     </rdf:li>
     <rdf:li rdf:parseType="Resource">
      <Container:Item Item:Mime="$clipMime" Item:Semantic="MotionPhoto" Item:Length="$clipLength" Item:Padding="0"/>
     </rdf:li>
    </rdf:Seq>
   </Container:Directory>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
}
