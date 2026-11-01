package com.kgs.calendar.ui.platform

import com.kgs.calendar.ui.LocationSuggestion
import java.net.URLEncoder

internal fun openStreetMapUrl(suggestion: LocationSuggestion): String =
    "https://www.openstreetmap.org/?mlat=${suggestion.latitude}&mlon=${suggestion.longitude}" +
        "#map=16/${suggestion.latitude}/${suggestion.longitude}"

internal fun openStreetMapSearchUrl(query: String): String =
    "https://www.openstreetmap.org/search?query=" + URLEncoder.encode(query, Charsets.UTF_8).replace("+", "%20")
