package com.gankdat.pomegranate.ui.onboarding

/** What the app can tell about a chosen SAF tree from its document id alone. */
internal data class DestinationInfo(
    /** `primary` for internal storage, otherwise the volume's UUID (SD card, USB). */
    val volume: String,
    /** Path inside the volume, empty for the volume root. */
    val path: String,
    val isRemovable: Boolean,
    /** Inside a folder Google Photos commonly backs up, so pulled files could be re-uploaded. */
    val isBackupFolder: Boolean,
) {
    /** Short label for the UI, e.g. `SD card › Photos` or `Internal storage`. */
    fun label(internalName: String, removableName: String): String {
        val root = if (isRemovable) removableName else internalName
        return if (path.isEmpty()) root else "$root › ${path.replace("/", " › ")}"
    }
}

/** Pure parsing of `DocumentsContract` tree ids such as `primary:Pictures/Pomegranate` or `1234-ABCD:`. */
internal object DestinationFolders {
    private const val PRIMARY_VOLUME = "primary"
    private val backupRoots = setOf("dcim", "pictures", "download", "screenshots", "movies")

    fun describe(treeDocumentId: String): DestinationInfo {
        val volume = treeDocumentId.substringBefore(':', missingDelimiterValue = treeDocumentId)
        val path = treeDocumentId.substringAfter(':', missingDelimiterValue = "").trim('/')
        val firstSegment = path.substringBefore('/').lowercase()
        return DestinationInfo(
            volume = volume,
            path = path,
            isRemovable = volume != PRIMARY_VOLUME,
            isBackupFolder = firstSegment in backupRoots,
        )
    }
}
