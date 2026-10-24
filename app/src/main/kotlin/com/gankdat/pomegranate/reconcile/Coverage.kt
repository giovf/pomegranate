package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.model.MediaState
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** How a month looks from the index's point of view; drives the "pick this month next" hint. */
internal enum class CoverageHint {
    /** Phone has items this month, the cloud index has none: the month was probably never picked. */
    NOT_INDEXED,

    /** Some phone items this month have no match in the index. */
    PARTIAL,

    /** Every phone item this month is matched. */
    COVERED,

    /** Only cloud items this month: nothing on the phone to compare against. */
    CLOUD_ONLY,
}

internal data class MonthCoverage(
    /** Null for items with no usable date. */
    val month: YearMonth?,
    val deviceCount: Int,
    /** Phone items matched to an indexed cloud item. */
    val deviceMatched: Int,
    val cloudCount: Int,
    /** Indexed cloud items with no phone copy: what a pull would bring down. */
    val cloudOnly: Int,
) {
    val hint: CoverageHint = when {
        deviceCount == 0 -> CoverageHint.CLOUD_ONLY
        cloudCount == 0 -> CoverageHint.NOT_INDEXED
        deviceMatched < deviceCount -> CoverageHint.PARTIAL
        else -> CoverageHint.COVERED
    }
}

/**
 * Per-month comparison of the device census with the cloud index. Google
 * never reports the library size, so this is the only way to see which
 * months still need a picker round. A month of unmatched phone photos is a
 * hint, not proof: those photos may simply never have been backed up.
 */
internal object CoverageCalculator {
    fun byMonth(device: List<DeviceItemEntity>, cloud: List<CloudItemEntity>, states: StateIndex, zone: ZoneId = ZoneId.systemDefault()): List<MonthCoverage> {
        fun month(millis: Long): YearMonth? = if (millis > 0) YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone)) else null

        val deviceByMonth = device.groupBy { month(it.dateTaken) }
        val cloudByMonth = cloud.groupBy { month(it.createTime) }
        val months = (deviceByMonth.keys + cloudByMonth.keys).distinct()
        return months.map { m ->
            val d = deviceByMonth[m].orEmpty()
            val c = cloudByMonth[m].orEmpty()
            MonthCoverage(
                month = m,
                deviceCount = d.size,
                deviceMatched = d.count { states.deviceStates[it.id] == MediaState.BOTH },
                cloudCount = c.size,
                cloudOnly = c.count { (states.cloudStates[it.id] ?: MediaState.CLOUD_ONLY) == MediaState.CLOUD_ONLY },
            )
        }.sortedWith(compareByDescending<MonthCoverage> { it.month != null }.thenByDescending { it.month })
    }
}
