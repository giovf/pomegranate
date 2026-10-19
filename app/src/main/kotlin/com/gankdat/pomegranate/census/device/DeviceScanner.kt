package com.gankdat.pomegranate.census.device

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity

/**
 * MediaStore-backed [MediaStoreSource]. Queries the unified Files table for
 * images and videos so one projection serves both, and fills in missing
 * date-taken values from EXIF for a bounded number of images per scan.
 */
internal class DeviceScanner(context: Context) : MediaStoreSource {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private var exifReadsThisScan = 0

    override fun volumes(): List<String> {
        val names = MediaStore.getExternalVolumeNames(appContext)
        return listOf(MediaStore.VOLUME_EXTERNAL_PRIMARY) + (names - MediaStore.VOLUME_EXTERNAL_PRIMARY).sorted()
    }

    override fun count(volume: String, modifiedAfterSeconds: Long?): Int {
        val (selection, args) = selection(modifiedAfterSeconds)
        return resolver.query(filesUri(volume), arrayOf(MediaStore.Files.FileColumns._ID), selection, args, null)
            ?.use { it.count } ?: 0
    }

    override fun ids(volume: String): List<Long> {
        val (selection, args) = selection(null)
        val ids = ArrayList<Long>()
        resolver.query(filesUri(volume), arrayOf(MediaStore.Files.FileColumns._ID), selection, args, null)?.use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        return ids
    }

    override fun forEach(volume: String, modifiedAfterSeconds: Long?, consumer: (ScannedMediaItem) -> Unit) {
        exifReadsThisScan = 0
        val (selection, args) = selection(modifiedAfterSeconds)
        val sort = "${MediaStore.Files.FileColumns._ID} ASC"
        resolver.query(filesUri(volume), PROJECTION, selection, args, sort)?.use { cursor ->
            val col = Columns(cursor)
            while (cursor.moveToNext()) consumer(cursor.toItem(volume, col))
        }
    }

    private fun Cursor.toItem(volume: String, c: Columns): ScannedMediaItem {
        val id = getLong(c.id)
        val isVideo = getInt(c.mediaType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        val mime = getStringOrEmpty(c.mime)
        val dateModifiedMs = getLong(c.dateModified) * 1_000
        var dateTaken = if (isNull(c.dateTaken)) 0L else getLong(c.dateTaken)
        if (dateTaken <= 0L && !isVideo && exifReadsThisScan < MAX_EXIF_READS_PER_SCAN) {
            exifReadsThisScan++
            dateTaken = readExifDateTaken(contentUri(volume, id)) ?: 0L
        }
        return ScannedMediaItem(
            mediaStoreId = id,
            volume = volume,
            relativePath = getStringOrEmpty(c.relativePath),
            bucket = getStringOrEmpty(c.bucket),
            displayName = getStringOrEmpty(c.displayName),
            mime = mime,
            size = if (isNull(c.size)) 0L else getLong(c.size),
            dateTaken = dateTaken,
            dateModified = dateModifiedMs,
            width = if (isNull(c.width)) 0 else getInt(c.width),
            height = if (isNull(c.height)) 0 else getInt(c.height),
            durationMs = if (isVideo && !isNull(c.duration)) getLong(c.duration) else null,
            isVideo = isVideo,
        )
    }

    private fun readExifDateTaken(uri: Uri): Long? = try {
        resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            ExifDates.toEpochMillis(
                dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
                offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
            )
        }
    } catch (_: java.io.IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun selection(modifiedAfterSeconds: Long?): Pair<String, Array<String>> {
        val base = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val baseArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        return if (modifiedAfterSeconds == null) {
            base to baseArgs
        } else {
            "$base AND ${MediaStore.Files.FileColumns.DATE_MODIFIED} > ?" to (baseArgs + modifiedAfterSeconds.toString())
        }
    }

    private class Columns(cursor: Cursor) {
        val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val mediaType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val relativePath = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
        val bucket = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        val displayName = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val mime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val dateTaken = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
        val dateModified = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val width = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
        val height = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
    }

    companion object {
        /** EXIF reads open each file; cap them so a large library with no DATE_TAKEN cannot stall a scan. */
        const val MAX_EXIF_READS_PER_SCAN: Int = 1_000

        private val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
        )

        private fun filesUri(volume: String): Uri = MediaStore.Files.getContentUri(volume)

        /** Content URI for a scanned item; valid for both images and videos. */
        fun contentUri(volume: String, mediaStoreId: Long): Uri =
            ContentUris.withAppendedId(MediaStore.Files.getContentUri(volume), mediaStoreId)

        private fun Cursor.getStringOrEmpty(column: Int): String = if (isNull(column)) "" else getString(column)
    }
}

/** The content URI a device item can be opened or thumbnailed through. */
internal fun DeviceItemEntity.contentUri(): Uri = DeviceScanner.contentUri(volume, mediaStoreId)
