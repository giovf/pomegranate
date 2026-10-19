package com.gankdat.pomegranate.auth

import java.net.URLDecoder

/** Decodes the query component of a redirect URL; pure so the redirect handling is testable. */
internal object QueryParams {
    fun parse(url: String): Map<String, String> {
        val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        if (query.isEmpty()) return emptyMap()
        return query.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
            val value = if ('=' in pair) URLDecoder.decode(pair.substringAfter('='), "UTF-8") else ""
            key to value
        }
    }
}
