package com.example.data.sync

import com.example.data.model.SyncedCalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object IcsCalendarParser {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val keepUrlRegex = Regex("""https?://keep\.google\.com/[^\s\n"'<>]+""", RegexOption.IGNORE_CASE)

    private val windowsTzAliases = mapOf(
        "pakistan standard time" to "Asia/Karachi",
        "india standard time" to "Asia/Kolkata",
        "arabian standard time" to "Asia/Dubai",
        "gmt standard time" to "Europe/London",
        "greenwich standard time" to "UTC",
        "eastern standard time" to "America/New_York",
        "central standard time" to "America/Chicago",
        "mountain standard time" to "America/Denver",
        "pacific standard time" to "America/Los_Angeles",
        "w. europe standard time" to "Europe/Berlin",
        "central european standard time" to "Europe/Warsaw",
    )

    fun normalizeCalendarUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().replace("\n", "").replace("\r", "")
        if (trimmed.isBlank()) return ""

        val schemeFixed = when {
            trimmed.startsWith("webcal://", ignoreCase = true) ->
                "https://" + trimmed.substring(9)
            trimmed.startsWith("webcals://", ignoreCase = true) ->
                "https://" + trimmed.substring(10)
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) ->
                trimmed
            else -> "https://$trimmed"
        }

        // Convert Google Calendar share / embed links (?src=... or ?cid=...) into public .ics feed links
        return runCatching {
            val uri = URI(schemeFixed)
            val host = uri.host.orEmpty().lowercase(Locale.US)
            val path = uri.path.orEmpty()
            if (host.contains("calendar.google.com") && !path.endsWith(".ics", ignoreCase = true)) {
                val queryParams = parseQueryParams(uri.rawQuery.orEmpty())
                val srcParam = queryParams["src"].orEmpty().trim()
                val cidParam = queryParams["cid"].orEmpty().trim()
                val calId = when {
                    srcParam.isNotBlank() -> srcParam
                    cidParam.isNotBlank() -> decodeCidIfBase64(cidParam)
                    else -> ""
                }
                if (calId.isNotBlank()) {
                    val encoded = URLEncoder.encode(calId, "UTF-8")
                    return@runCatching "https://calendar.google.com/calendar/ical/$encoded/public/basic.ics"
                }
            }
            schemeFixed
        }.getOrDefault(schemeFixed)
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                key to value
            } else {
                null
            }
        }.toMap()
    }

    private fun decodeCidIfBase64(cid: String): String {
        if (cid.contains('@')) return cid
        return runCatching {
            val decoded = String(decodeBase64Bytes(cid), Charsets.UTF_8).trim()
            if (decoded.contains('@') || decoded.contains("group.calendar.google.com")) decoded else cid
        }.getOrDefault(cid)
    }

    private fun decodeBase64Bytes(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val clean = input.replace('-', '+').replace('_', '/').filter { it in alphabet || it == '=' }
        val out = ArrayList<Byte>((clean.length * 3) / 4)
        var buffer = 0
        var bits = 0
        for (ch in clean) {
            if (ch == '=') break
            val v = alphabet.indexOf(ch)
            if (v < 0) continue
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    suspend fun fetchAndParse(
        feedUrl: String,
        sourceId: String,
        sourceLabel: String,
    ): Result<List<SyncedCalendarEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = normalizeCalendarUrl(feedUrl)
            require(normalizedUrl.isNotBlank()) { "Please enter a valid calendar feed link." }
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "DilawarSaqlainCMS/1.0")
                .header("Accept", "text/calendar, text/plain, */*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Calendar link returned HTTP ${response.code}. Verify the calendar link is public or uses the Secret iCal (.ics) address.")
                }
                val body = response.body?.string() ?: error("Calendar link returned empty data.")
                if (!body.contains("BEGIN:VCALENDAR", ignoreCase = true)) {
                    error("This URL returned a web page instead of a calendar feed. In Google Calendar Settings -> Integrate calendar, copy the 'Secret address in iCal format' (.ics link).")
                }
                parseIcsContent(body, sourceId, sourceLabel)
            }
        }
    }

    fun parseIcsContent(
        icsText: String,
        sourceId: String,
        sourceLabel: String,
        referenceNowMs: Long = System.currentTimeMillis(),
    ): List<SyncedCalendarEvent> {
        val unfoldedLines = unfoldLines(icsText)
        val events = mutableListOf<SyncedCalendarEvent>()

        var inEvent = false
        var uid = ""
        var summary = ""
        var location = ""
        var description = ""
        var rrule = ""
        var startMs = 0L
        var endMs = 0L
        var allDay = false

        for (line in unfoldedLines) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true
                    uid = ""
                    summary = ""
                    location = ""
                    description = ""
                    rrule = ""
                    startMs = 0L
                    endMs = 0L
                    allDay = false
                }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    if (inEvent && summary.isNotBlank() && startMs > 0L) {
                        val durationMs = if (endMs > startMs) (endMs - startMs) else 3_600_000L
                        val keepUrl = keepUrlRegex.find(description)?.value.orEmpty()
                        val cleanTitle = unescapeIcsText(summary).take(240)
                        val cleanLoc = unescapeIcsText(location).take(240)
                        val cleanDesc = unescapeIcsText(description).take(2000)
                        val baseUid = uid.ifBlank { "${sourceId}_${startMs}_${summary.hashCode()}" }

                        val occurrences = if (rrule.isNotBlank()) {
                            expandRecurrence(
                                baseStartMs = startMs,
                                rrule = rrule,
                                windowStartMs = referenceNowMs - (30L * 86_400_000L),
                                windowEndMs = referenceNowMs + (180L * 86_400_000L),
                            )
                        } else {
                            listOf(startMs)
                        }

                        occurrences.forEachIndexed { idx, occStart ->
                            val occUid = if (occurrences.size == 1 && idx == 0 && rrule.isBlank()) {
                                baseUid
                            } else {
                                "${baseUid}_$occStart"
                            }
                            events.add(
                                SyncedCalendarEvent(
                                    externalUid = occUid,
                                    sourceId = sourceId,
                                    sourceLabel = sourceLabel,
                                    title = cleanTitle,
                                    location = cleanLoc,
                                    description = cleanDesc,
                                    startEpochMs = occStart,
                                    endEpochMs = occStart + durationMs,
                                    allDay = allDay,
                                    keepNoteUrl = keepUrl,
                                )
                            )
                        }
                    }
                    inEvent = false
                }
                inEvent -> {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val propPart = line.substring(0, colonIdx)
                        val valuePart = line.substring(colonIdx + 1)
                        val propName = propPart.substringBefore(';').uppercase(Locale.US)
                        when (propName) {
                            "UID" -> uid = valuePart.trim()
                            "SUMMARY" -> summary = valuePart
                            "LOCATION" -> location = valuePart
                            "DESCRIPTION" -> description = valuePart
                            "RRULE" -> rrule = valuePart.trim()
                            "DTSTART" -> {
                                val parsed = parseIcsDateTime(propPart, valuePart)
                                startMs = parsed.first
                                allDay = parsed.second
                            }
                            "DTEND" -> {
                                val parsed = parseIcsDateTime(propPart, valuePart)
                                endMs = parsed.first
                            }
                        }
                    }
                }
            }
        }
        return events.sortedBy { it.startEpochMs }
    }

    private fun expandRecurrence(
        baseStartMs: Long,
        rrule: String,
        windowStartMs: Long,
        windowEndMs: Long,
    ): List<Long> {
        val parts = rrule.split(';').mapNotNull { kv ->
            val idx = kv.indexOf('=')
            if (idx > 0) kv.substring(0, idx).uppercase(Locale.US) to kv.substring(idx + 1) else null
        }.toMap()

        val freq = parts["FREQ"]?.uppercase(Locale.US) ?: return listOf(baseStartMs)
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val maxCount = parts["COUNT"]?.toIntOrNull()?.coerceIn(1, 60) ?: 40
        val untilMs = parts["UNTIL"]?.let { parseIcsDateTime("", it).first }?.takeIf { it > 0L } ?: windowEndMs
        val hardEndMs = minOf(untilMs, windowEndMs)

        val cal = Calendar.getInstance().apply { timeInMillis = baseStartMs }
        val result = mutableListOf<Long>()
        var generated = 0
        var guard = 0
        while (generated < maxCount && guard < 400 && cal.timeInMillis <= hardEndMs) {
            val t = cal.timeInMillis
            if (t >= windowStartMs) {
                result.add(t)
            }
            generated++
            guard++
            when (freq) {
                "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, interval)
                "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, interval)
                "MONTHLY" -> cal.add(Calendar.MONTH, interval)
                "YEARLY" -> cal.add(Calendar.YEAR, interval)
                else -> break
            }
        }
        return if (result.isEmpty()) listOf(baseStartMs) else result
    }

    private fun unfoldLines(raw: String): List<String> {
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
        val result = mutableListOf<String>()
        for (line in normalized.split('\n')) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.substring(1)
            } else if (line.isNotBlank()) {
                result.add(line)
            }
        }
        return result
    }

    private fun unescapeIcsText(text: String): String =
        text.replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
            .trim()

    private fun parseIcsDateTime(propPart: String, rawValue: String): Pair<Long, Boolean> {
        val value = rawValue.trim()
        val hasDateParam = propPart.contains("VALUE=DATE", ignoreCase = true)
        val isDateOnly = (value.length == 8 && value.all { it.isDigit() }) || (hasDateParam && value.length == 8)
        if (isDateOnly) {
            val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
            val date = fmt.parse(value) ?: return 0L to true
            val cal = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis to true
        }

        val tzId = propPart.split(';')
            .firstOrNull { it.startsWith("TZID=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.removeSurrounding("\"")

        val resolvedTzId = tzId?.let { id ->
            windowsTzAliases[id.lowercase(Locale.US)] ?: id
        }

        val timeZone = when {
            value.endsWith("Z", ignoreCase = true) -> TimeZone.getTimeZone("UTC")
            !resolvedTzId.isNullOrBlank() -> TimeZone.getTimeZone(resolvedTzId)
            else -> TimeZone.getDefault()
        }

        val cleanValue = value.removeSuffix("Z").removeSuffix("z")
        val patterns = listOf("yyyyMMdd'T'HHmmss", "yyyyMMdd'T'HHmm")
        for (pattern in patterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply { this.timeZone = timeZone }
                val parsed = fmt.parse(cleanValue)
                if (parsed != null) return parsed.time to false
            } catch (_: Exception) {
            }
        }
        return 0L to false
    }
}
