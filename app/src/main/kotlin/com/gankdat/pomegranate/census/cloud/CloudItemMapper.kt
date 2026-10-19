package com.gankdat.pomegranate.census.cloud

import com.gankdat.pomegranate.data.db.entity.CloudItemEntity

internal object CloudItemMapper {
    /** Google documents base URLs as valid for 60 minutes. */
    const val BASE_URL_LIFETIME_MILLIS: Long = 60 * 60_000

    fun toEntity(item: PickedMediaItem, sessionId: String, listedAtMillis: Long): CloudItemEntity = CloudItemEntity(
        pickerMediaItemId = item.id,
        sessionId = sessionId,
        filename = item.filename,
        mime = item.mimeType,
        type = item.type,
        createTime = item.createTimeMillis,
        width = item.width,
        height = item.height,
        durationMs = null,
        cameraMake = item.cameraMake,
        cameraModel = item.cameraModel,
        baseUrl = item.baseUrl,
        baseUrlExpiresAt = listedAtMillis + BASE_URL_LIFETIME_MILLIS,
    )
}
