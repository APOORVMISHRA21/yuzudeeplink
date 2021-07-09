/*
 * Copyright (C) 2017-2019 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.adblock

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.core.util.PatternsCompat
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.core.utility.utils.getMimeTypeFromExtension
import okhttp3.internal.publicsuffix.PublicSuffix
import java.util.*

const val BROADCAST_ACTION_UPDATE_AD_BLOCK_DATA = "jp.hazuki.yuzubrowser.adblock.broadcast.update.adblock"

fun WebResourceRequest.isThirdParty(pageUri: Uri): Boolean {
    val hostName = url.host ?: return true
    val pageHost = pageUri.host ?: return true

    if (hostName == pageHost) return false

    val ipPattern = PatternsCompat.IP_ADDRESS
    if (ipPattern.matcher(hostName).matches() || ipPattern.matcher(pageHost).matches()) {
        return true
    }

    val db = PublicSuffix.get()

    return db.getEffectiveTldPlusOne(hostName) != db.getEffectiveTldPlusOne(pageHost)
}


fun WebResourceRequest.getContentRequest(pageUri: Uri) =
    ContentRequest(url, pageUri, getContentType(pageUri), isThirdParty(pageUri))

private fun WebResourceRequest.getContentType(pageUri: Uri): Int {
    var type = 0
    val scheme = url.scheme
    var isPage = false

    if (isForMainFrame) {
        if (url == pageUri) {
            isPage = true
            type = ContentRequest.TYPE_DOCUMENT
        }
    } else {
        type = ContentRequest.TYPE_SUB_DOCUMENT
    }

    if (scheme == "ws" || scheme == "wss") {
        type = type or ContentRequest.TYPE_WEB_SOCKET
    }

    if (requestHeaders["X-Requested-With"] == "XMLHttpRequest") {
        type = type or ContentRequest.TYPE_XHR
    }

    val path = url.path ?: url.toString()
    val lastDot = path.lastIndexOf('.')
    if (lastDot >= 0) {
        when (val extension = path.substring(lastDot + 1).toLowerCase(Locale.ENGLISH)) {
            "js" -> return type or ContentRequest.TYPE_SCRIPT
            "css" -> return type or ContentRequest.TYPE_STYLE_SHEET
            "otf", "ttf", "ttc", "woff", "woff2" -> return type or ContentRequest.TYPE_FONT
            "php" -> Unit
            else -> {
                val mimeType = getMimeTypeFromExtension(extension)
                if (mimeType != "application/octet-stream") {
                    return type or mimeType.getFilterType()
                }
            }
        }
    }

    if (isPage) {
        return type or ContentRequest.TYPE_OTHER
    }

    val accept = requestHeaders["Accept"]
    return if (accept != null && accept != "*/*") {
        val mimeType = accept.split(',')[0]
        type or mimeType.getFilterType()
    } else {
        type or ContentRequest.TYPE_OTHER or ContentRequest.TYPE_MEDIA or ContentRequest.TYPE_IMAGE or
            ContentRequest.TYPE_FONT or ContentRequest.TYPE_STYLE_SHEET or ContentRequest.TYPE_SCRIPT
    }
}

private fun String.getFilterType(): Int {
    return when (this) {
        "application/javascript",
        "application/x-javascript",
        "text/javascript",
        "application/json" -> ContentRequest.TYPE_SCRIPT
        "text/css" -> ContentRequest.TYPE_STYLE_SHEET
        else -> when {
            startsWith("image/") -> ContentRequest.TYPE_IMAGE
            startsWith("video/") || startsWith("audio/") -> ContentRequest.TYPE_MEDIA
            startsWith("font/") -> ContentRequest.TYPE_FONT
            else -> ContentRequest.TYPE_OTHER
        }
    }
}
