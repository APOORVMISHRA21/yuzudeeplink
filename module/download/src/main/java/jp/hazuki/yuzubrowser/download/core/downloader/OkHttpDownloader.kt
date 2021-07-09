/*
 * Copyright (C) 2017-2021 Hazuki
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

package jp.hazuki.yuzubrowser.download.core.downloader

import android.content.Context
import android.webkit.CookieManager
import androidx.documentfile.provider.DocumentFile
import jp.hazuki.yuzubrowser.core.MIME_TYPE_UNKNOWN
import jp.hazuki.yuzubrowser.core.utility.log.ErrorReport
import jp.hazuki.yuzubrowser.core.utility.storage.toDocumentFile
import jp.hazuki.yuzubrowser.download.TMP_FILE_SUFFIX
import jp.hazuki.yuzubrowser.download.core.data.DownloadFileInfo
import jp.hazuki.yuzubrowser.download.core.data.DownloadRequest
import jp.hazuki.yuzubrowser.download.core.utils.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.HttpURLConnection

class OkHttpDownloader(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val info: DownloadFileInfo,
    private val downloadRequest: DownloadRequest,
) : Downloader {
    override var downloadListener: Downloader.DownloadListener? = null

    private var abort = false

    override fun download(): Boolean {
        val requestBuilder = Request.Builder()
            .url(info.url)
            .get()
            .setCookie(CookieManager.getInstance().getCookie(info.url))
            .setReferrer(downloadRequest.referrer)
            .setUserAgent(context, downloadRequest.userAgent)

        val rootFile = info.root.toDocumentFile(context)
        val downloadFile = if (downloadRequest.isScopedStorageMode) {
            rootFile
        } else {
            val existTmp = rootFile.findFile("${info.name}$TMP_FILE_SUFFIX")

            if (info.resumable && info.checkFlag(DownloadFileInfo.STATE_PAUSED) && existTmp != null) {
                info.currentSize = existTmp.length()
                requestBuilder.header("Range", "bytes=${info.currentSize}-${info.size}")
            }

            val mimeType = if (info.mimeType.isNotEmpty()) info.mimeType else MIME_TYPE_UNKNOWN

            val tmpFile = existTmp ?: rootFile.createFile(mimeType, "${info.name}$TMP_FILE_SUFFIX")

            if (tmpFile == null) {
                info.state = DownloadFileInfo.STATE_UNKNOWN_ERROR
                downloadListener?.onFileDownloadFailed(info, "Unable to create file, file name:${info.name}")
                return false
            }

            tmpFile
        }

        val call = okHttpClient.newCall(requestBuilder.build())
        try {
            val response = call.execute()

            val mode = when (response.code) {
                HttpURLConnection.HTTP_OK -> "w"
                HttpURLConnection.HTTP_PARTIAL -> "wa"
                else -> {
                    info.state = DownloadFileInfo.STATE_UNKNOWN_ERROR
                    downloadListener?.onFileDownloadFailed(info, "failed connect response:${response.code}\nat:${info.url}")
                    return false
                }
            }
            if (info.size < 0) {
                info.size = response.contentLength
            }
            if (!info.resumable) {
                info.resumable = response.isResumable
            }
            val os = context.contentResolver.openOutputStream(downloadFile.uri, mode)
                ?: throw DownloadException("Can not open file. mimetype:${info.mimeType}, filename:${info.name}$TMP_FILE_SUFFIX, Exists:${rootFile.exists()}, Writable:${rootFile.canWrite()}, Uri:${rootFile.uri}")

            BufferedOutputStream(os).use { out ->
                response.body!!.source().use { source ->
                    downloadListener?.onStartDownload(info)
                    var len: Int
                    var progress = info.currentSize
                    val buffer = ByteArray(BUFFER_SIZE)
                    var oldSize: Long
                    var oldSec = System.currentTimeMillis()

                    while (source.read(buffer, 0, BUFFER_SIZE).also { len = it } >= 0) {
                        if (abort) break

                        out.write(buffer, 0, len)
                        progress += len

                        if (System.currentTimeMillis() > oldSec + NOTIFICATION_INTERVAL) {
                            oldSize = info.currentSize
                            info.currentSize = progress

                            val time = System.currentTimeMillis()
                            info.transferSpeed = ((progress - oldSize) * 1000.0 / (time - oldSec)).toLong()
                            downloadListener?.onFileDownloading(info, progress)

                            oldSec = time
                        }
                    }
                    out.flush()
                }
            }

            var downloadedFile: DocumentFile? = null

            if (abort) {
                if (downloadRequest.isScopedStorageMode) {
                    if (rootFile.exists()) {
                        context.contentResolver.delete(rootFile.uri, null, null)
                    }
                } else {
                    deleteTempIfNeed(rootFile)
                }
                downloadListener?.onFileDownloadAbort(info)
                return false
            } else {
                if (downloadRequest.isScopedStorageMode) {
                    downloadedFile = rootFile
                } else {
                    if (!downloadFile.renameTo(info.name)) {
                        downloadedFile = rootFile.findFile(info.name)
                        if (downloadedFile == null)
                            throw DownloadException("Rename is failed. name:\"${info.name}\", download path:${rootFile.uri}, mimetype:${info.mimeType}, exists:${rootFile.findFile(info.name) != null}")
                    }

                    downloadedFile = downloadedFile ?: rootFile.findFile(info.name)
                    if (downloadedFile == null) {
                        throw DownloadException("File not found. name:\"${info.name}\", download path:${rootFile.uri}")
                    }
                }
            }

            info.state = DownloadFileInfo.STATE_DOWNLOADED
            downloadListener?.onFileDownloaded(info, downloadedFile)
            return true

        } catch (e: IOException) {
            ErrorReport.printAndWriteLog(e)
            if (info.resumable) {
                info.state = DownloadFileInfo.STATE_UNKNOWN_ERROR or DownloadFileInfo.STATE_PAUSED
            } else {
                if (downloadFile.exists()) {
                    downloadFile.delete()
                }
                info.state = DownloadFileInfo.STATE_UNKNOWN_ERROR
            }
            if (e is DownloadException) {
                downloadListener?.onFileDownloadFailed(info, e.message)
            } else {
                downloadListener?.onFileDownloadFailed(info, null)
            }
        }
        return false
    }

    override fun cancel() {
        info.state = DownloadFileInfo.STATE_CANCELED
        abort = true
    }

    override fun pause() {
        info.state = DownloadFileInfo.STATE_PAUSED
        abort = true
    }

    override fun abort() {
        if (info.resumable) {
            info.state = DownloadFileInfo.STATE_UNKNOWN_ERROR or DownloadFileInfo.STATE_PAUSED
        } else {
            info.state = DownloadFileInfo.STATE_UNKNOWN_ERROR
        }
        abort = true
    }

    private fun deleteTempIfNeed(rootFile: DocumentFile) {
        if (info.state == DownloadFileInfo.STATE_UNKNOWN_ERROR || info.state == DownloadFileInfo.STATE_CANCELED) {
            rootFile.findFile("${info.name}$TMP_FILE_SUFFIX")?.delete()
        }
    }

    private class DownloadException(message: String) : IOException(message)

    companion object {
        private const val BUFFER_SIZE = 1024 * 2
        private const val NOTIFICATION_INTERVAL = 1000
    }
}
