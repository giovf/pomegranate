package com.gankdat.pomegranate.reconcile.origin

import java.util.Locale

/**
 * Filename and folder conventions of common apps. Filename matches are
 * stronger evidence than folder matches because a cloud item carries no
 * folder; folder patterns mainly canonicalise learned rules' labels.
 */
internal object BuiltinPatterns {
    const val FILENAME_CONFIDENCE: Float = 0.9f
    const val BUCKET_CONFIDENCE: Float = 0.8f

    private val ci = RegexOption.IGNORE_CASE

    val rules: List<BuiltinRule> = listOf(
        BuiltinRule(
            origin = "WhatsApp",
            filenamePatterns = listOf(Regex("""^(IMG|VID|AUD|PTT)-\d{8}-WA\d{4}""", ci)),
            bucketPatterns = listOf(Regex("whatsapp", ci)),
        ),
        BuiltinRule(
            origin = "Telegram",
            filenamePatterns = listOf(Regex("""^(IMG|VID)_\d{8}_\d{6}_\d{3}\.""", ci)),
            bucketPatterns = listOf(Regex("telegram", ci)),
        ),
        BuiltinRule(
            origin = "Screenshots",
            filenamePatterns = listOf(Regex("""^Screenshot[_-]""", ci)),
            bucketPatterns = listOf(Regex("^screenshots?$", ci)),
        ),
        BuiltinRule(
            origin = "Screen recordings",
            filenamePatterns = listOf(Regex("""^Screen[_ -]?Record(ing)?[_-]""", ci)),
            bucketPatterns = listOf(Regex("^screen ?record(ing)?s?$", ci)),
        ),
        BuiltinRule(
            origin = "Camera",
            filenamePatterns = listOf(
                Regex("""^PXL_\d{8}_\d{9}""", ci), // Pixel
                Regex("""^IMG_\d{8}_\d{6}(?!_\d{3}\.)""", ci), // generic Android camera (not Telegram's extra counter)
                Regex("""^VID_\d{8}_\d{6}(?!_\d{3}\.)""", ci),
                Regex("""^MVIMG_\d{8}_\d{6}""", ci),
                Regex("""^DSC[_-]?\d{4,}""", ci),
                Regex("""^IMG_\d{4}\.(jpg|jpeg|heic|heif|dng|mov|mp4)$""", ci), // iPhone
                Regex("""^\d{8}_\d{6}\.(jpg|jpeg|mp4|heic)$""", ci), // Samsung
            ),
            bucketPatterns = listOf(Regex("^camera$", ci), Regex("^dcim$", ci), Regex("^100(apple|andro|media)", ci)),
        ),
        BuiltinRule(origin = "Instagram", filenamePatterns = emptyList(), bucketPatterns = listOf(Regex("instagram", ci))),
        BuiltinRule(origin = "Facebook", filenamePatterns = listOf(Regex("""^FB_IMG_\d+""", ci)), bucketPatterns = listOf(Regex("facebook", ci))),
        BuiltinRule(origin = "X", filenamePatterns = emptyList(), bucketPatterns = listOf(Regex("^(twitter|x)$", ci))),
        BuiltinRule(origin = "Snapchat", filenamePatterns = listOf(Regex("""^Snapchat-\d+""", ci)), bucketPatterns = listOf(Regex("snapchat", ci))),
        BuiltinRule(origin = "Signal", filenamePatterns = listOf(Regex("""^signal-\d{4}-\d{2}-\d{2}""", ci)), bucketPatterns = listOf(Regex("^signal$", ci))),
        BuiltinRule(origin = "Downloads", filenamePatterns = emptyList(), bucketPatterns = listOf(Regex("^downloads?$", ci))),
    )

    fun matchFilename(filename: String): BuiltinRule? = rules.firstOrNull { rule -> rule.filenamePatterns.any { it.containsMatchIn(filename) } }

    fun matchBucket(bucket: String): BuiltinRule? {
        val trimmed = bucket.trim()
        return rules.firstOrNull { rule -> rule.bucketPatterns.any { it.containsMatchIn(trimmed) } }
    }

    /** A readable origin label for a device folder: the app name if known, else the folder name itself. */
    fun labelForBucket(bucket: String): String = matchBucket(bucket)?.origin ?: bucket.trim().lowercase(Locale.ROOT)
        .replaceFirstChar { it.titlecase(Locale.ROOT) }
}
