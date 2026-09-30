package com.focustag.app.domain

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * QR / deep-link contract: same registered tag identity as NFC.
 *
 * Accepted payloads:
 * - `focustag://tag/{uid}`
 * - `focustag://tag/{uid}?…`
 * - raw hex UID (`1D1D701C1A1080` or `1D:1D:70:1C:1A:10:80`)
 */
object TagLinkParser {

    fun extractUid(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val decoded = try {
            URLDecoder.decode(raw.trim(), StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            raw.trim()
        }

        val candidate = when {
            decoded.contains("://tag/", ignoreCase = true) ->
                decoded.substringAfter("://tag/", missingDelimiterValue = "")
            decoded.contains(":/tag/", ignoreCase = true) ->
                decoded.substringAfter(":/tag/", missingDelimiterValue = "")
            decoded.startsWith("tag/", ignoreCase = true) ->
                decoded.removePrefix("tag/").removePrefix("TAG/")
            else -> decoded
        }
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')
            .trim()

        if (candidate.isBlank()) return null
        return NfcProtocol.normalize(candidate)
    }
}
