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

package jp.hazuki.yuzubrowser.adblock.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import androidx.core.app.JobIntentService
import androidx.core.content.getSystemService
import dagger.android.AndroidInjection
import jp.hazuki.yuzubrowser.adblock.BROADCAST_ACTION_UPDATE_AD_BLOCK_DATA
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.ElementFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.getFilterDir
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.ElementWriter
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import jp.hazuki.yuzubrowser.adblock.repository.AdBlockPref
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDatabase
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import jp.hazuki.yuzubrowser.core.eventbus.LocalEventBus
import jp.hazuki.yuzubrowser.core.utility.extensions.isConnectedWifi
import jp.hazuki.yuzubrowser.core.utility.log.ErrorReport
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject

class AbpUpdateService : JobIntentService() {

    @Inject
    internal lateinit var okHttpClient: OkHttpClient

    @Inject
    internal lateinit var abpDatabase: AbpDatabase

    override fun onHandleWork(intent: Intent) {
        when (intent.action) {
            ACTION_UPDATE_ABP -> {
                val param1 = intent.getParcelableExtra<AbpEntity>(EXTRA_ABP_ENTRY)!!
                val result = intent.getParcelableExtra<ResultReceiver?>(EXTRA_RESULT)
                updateAbpEntity(param1, result)
            }
            ACTION_UPDATE_ALL -> {
                val forceUpdate = intent.getBooleanExtra(EXTRA_FORCE_UPDATE, false)
                val result = intent.getParcelableExtra<ResultReceiver?>(EXTRA_RESULT)
                updateAll(forceUpdate, result)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    private fun updateAll(forceUpdate: Boolean, resultReceiver: ResultReceiver?) = runBlocking {
        var result = false
        var nextUpdateTime = Long.MAX_VALUE
        val now = System.currentTimeMillis()
        abpDatabase.abpDao().getAll().forEach {
            if (forceUpdate || it.isNeedUpdate()) {
                val localResult = updateInternal(it, forceUpdate)
                if (localResult && it.expires > 0) {
                    val nextTime = it.expires * AN_HOUR + now
                    if (nextTime < nextUpdateTime) nextUpdateTime = nextTime
                }
                result = result or localResult
            }
        }

        AdBlockPref.get(applicationContext).abpNextUpdateTime = if (nextUpdateTime != Long.MAX_VALUE) {
            nextUpdateTime
        } else {
            System.currentTimeMillis() + A_DAY
        }
        if (result) {
            LocalEventBus.getDefault().notify(BROADCAST_ACTION_UPDATE_AD_BLOCK_DATA)
        }
        resultReceiver?.send(RESULT_CODE_UPDATE_ALL, null)
    }

    private fun updateAbpEntity(entity: AbpEntity, result: ResultReceiver?) = runBlocking {
        if (updateInternal(entity)) {
            result?.send(RESULT_CODE_UPDATED, Bundle().apply { putParcelable(EXTRA_ABP_ENTRY, entity) })
            LocalEventBus.getDefault().notify(BROADCAST_ACTION_UPDATE_AD_BLOCK_DATA)
        } else {
            result?.send(RESULT_CODE_FAILED, Bundle().apply { putParcelable(EXTRA_ABP_ENTRY, entity) })
        }
    }

    private suspend fun updateInternal(entity: AbpEntity, forceUpdate: Boolean = false): Boolean {
        return when {
            entity.url == "yuzu://adblock/filter" -> updateAssets(entity)
            entity.url.startsWith("http") -> updateHttp(entity, forceUpdate)
            entity.url.startsWith("file") -> updateFile(entity)
            else -> false
        }
    }

    private suspend fun updateHttp(entity: AbpEntity, forceUpdate: Boolean): Boolean {

        val request = try {
            Request.Builder()
                .url(entity.url)
                .get()
        } catch (e: IllegalArgumentException) {
            return false
        }

        if (!forceUpdate) {
            entity.lastModified?.let {
                val dir = getFilterDir()

                if (dir.getAbpBlackListFile(entity).exists() ||
                    dir.getAbpWhiteListFile(entity).exists() ||
                    dir.getAbpWhitePageListFile(entity).exists())
                    request.addHeader("If-Modified-Since", it)
            }
        }

        val call = okHttpClient.newCall(request.build())
        try {
            val response = call.execute()

            if (response.code == 304) {
                entity.lastLocalUpdate = System.currentTimeMillis()
                abpDatabase.abpDao().update(entity)
                return false
            }
            response.body?.run {
                val charset = contentType()?.charset() ?: Charsets.UTF_8
                source().inputStream().bufferedReader(charset).use { reader ->
                    if (decode(reader, charset, entity)) {
                        entity.lastLocalUpdate = System.currentTimeMillis()
                        entity.lastModified = response.header("Last-Modified")
                        abpDatabase.abpDao().update(entity)
                        return true
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    private suspend fun updateFile(entity: AbpEntity): Boolean {
        val path = Uri.parse(entity.url).path ?: return false
        val file = File(path)
        if (file.lastModified() < entity.lastLocalUpdate) return false

        try {
            file.inputStream().bufferedReader().use { reader ->
                return decode(reader, Charsets.UTF_8, entity)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    private suspend fun updateAssets(entity: AbpEntity): Boolean {
        if (entity.version == "2") {
            val dir = getFilterDir()

            if (dir.getAbpBlackListFile(entity).exists() &&
                dir.getAbpWhiteListFile(entity).exists() &&
                dir.getAbpWhitePageListFile(entity).exists()) return false
        }

        assets.open("adblock/yuzu_filter.txt").bufferedReader().use {
            return decode(it, Charsets.UTF_8, entity)
        }
    }

    private suspend fun decode(reader: BufferedReader, charset: Charset, entity: AbpEntity): Boolean {
        val decoder = AbpFilterDecoder()
        if (!decoder.checkHeader(reader, charset)) return false

        val set = decoder.decode(reader, entity.url)

        val info = set.filterInfo
        entity.title = info.title
        entity.expires = info.expires ?: -1
        entity.homePage = info.homePage
        entity.version = info.version
        entity.lastUpdate = info.lastUpdate
        entity.lastLocalUpdate = System.currentTimeMillis()
        val dir = getFilterDir()

        val writer = FilterWriter()
        writer.write(dir.getAbpBlackListFile(entity), set.blackList)
        writer.write(dir.getAbpWhiteListFile(entity), set.whiteList)
        writer.write(dir.getAbpWhitePageListFile(entity), set.elementDisableFilter)

        val elementWriter = ElementWriter()
        elementWriter.write(dir.getAbpElementListFile(entity), set.elementList)

        abpDatabase.abpDao().update(entity)
        return true
    }

    private fun FilterWriter.write(file: File, list: List<UnifiedFilter>) {
        if (list.isNotEmpty()) {
            try {
                file.outputStream().buffered().use {
                    write(it, list)
                }
            } catch (e: IOException) {
                ErrorReport.printAndWriteLog(e)
            }
        } else {
            if (file.exists()) file.delete()
        }
    }

    private fun ElementWriter.write(file: File, list: List<ElementFilter>) {
        if (list.isNotEmpty()) {
            try {
                file.outputStream().buffered().use {
                    write(it, list)
                }
            } catch (e: IOException) {
                ErrorReport.printAndWriteLog(e)
            }
        } else {
            if (file.exists()) file.delete()
        }
    }

    companion object {
        private const val ACTION_UPDATE_ALL = "jp.hazuki.yuzubrowser.adblock.service.action.UpdateAll"
        private const val ACTION_UPDATE_ABP = "jp.hazuki.yuzubrowser.adblock.service.action.UpdateAbp"

        private const val EXTRA_ABP_ENTRY = "jp.hazuki.yuzubrowser.adblock.service.extra.entry"
        private const val EXTRA_RESULT = "jp.hazuki.yuzubrowser.adblock.service.extra.result"
        private const val EXTRA_FORCE_UPDATE = "jp.hazuki.yuzubrowser.adblock.service.extra.update.force"

        private const val RESULT_CODE_UPDATED = 1
        private const val RESULT_CODE_FAILED = 2
        private const val RESULT_CODE_UPDATE_ALL = 3

        private const val AN_HOUR = 60 * 60 * 1000
        private const val A_DAY = 24 * AN_HOUR

        private const val JOB_ID = 10

        fun updateAll(context: Context, forceUpdate: Boolean = false, result: UpdateResult? = null) {
            if (!forceUpdate) {
                val prefs = AdBlockPref.get(context.applicationContext)
                if (prefs.abpNextUpdateTime < System.currentTimeMillis()) return

                if (AppPrefs.abpUpdateWifiOnly.get()) {
                    val cm = context.getSystemService<ConnectivityManager>()!!
                    if (!cm.isConnectedWifi()) return
                }
            }

            val intent = Intent(context, AbpUpdateService::class.java).apply {
                action = ACTION_UPDATE_ALL
                putExtra(EXTRA_FORCE_UPDATE, forceUpdate)
                putExtra(EXTRA_RESULT, result)
            }

            enqueueWork(context, AbpUpdateService::class.java, JOB_ID, intent)
        }

        fun update(context: Context, abpEntity: AbpEntity, result: UpdateResult? = null) {
            val intent = Intent(context, AbpUpdateService::class.java).apply {
                action = ACTION_UPDATE_ABP
                putExtra(EXTRA_ABP_ENTRY, abpEntity)
                putExtra(EXTRA_RESULT, result)
            }

            enqueueWork(context, AbpUpdateService::class.java, JOB_ID, intent)
        }
    }

    abstract class UpdateResult(handler: Handler?) : ResultReceiver(handler) {

        final override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            when (resultCode) {
                RESULT_CODE_UPDATED -> onUpdated(resultData!!.getParcelable(EXTRA_ABP_ENTRY)!!)
                RESULT_CODE_FAILED -> onFailedUpdate(resultData!!.getParcelable(EXTRA_ABP_ENTRY)!!)
                RESULT_CODE_UPDATE_ALL -> onUpdateAll()
            }
        }

        abstract fun onFailedUpdate(entity: AbpEntity)

        abstract fun onUpdated(entity: AbpEntity)

        abstract fun onUpdateAll()
    }
}
