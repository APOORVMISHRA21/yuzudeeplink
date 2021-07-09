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

package jp.hazuki.yuzubrowser

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.databinding.library.BuildConfig
import com.google.firebase.FirebaseApp

import com.squareup.moshi.Moshi
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import jp.hazuki.yuzubrowser.adblock.registerAdBlockNotification
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDatabase
import jp.hazuki.yuzubrowser.core.utility.log.Logger
import jp.hazuki.yuzubrowser.core.utility.utils.createLanguageConfig
import jp.hazuki.yuzubrowser.di.DaggerAppComponent
import jp.hazuki.yuzubrowser.download.registerDownloadNotification
import jp.hazuki.yuzubrowser.legacy.settings.data.AppData
import jp.hazuki.yuzubrowser.provider.ProviderManager
import jp.hazuki.yuzubrowser.ui.BrowserApplication
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import javax.inject.Inject


class YuzuBrowserApplication : Application(), BrowserApplication, HasAndroidInjector {

    override val applicationId = BuildConfig.LIBRARY_PACKAGE_NAME
    override val permissionAppSignature = PERMISSION_MYAPP_SIGNATURE
    override val browserState = BrowserStateImpl()
    override val providerManager = ProviderManager()
    override val context: Context
        get() = this

    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    @Inject
    override lateinit var moshi: Moshi

    @Inject
    lateinit var abpDatabase: AbpDatabase

    private var isNeedInject = true

    override fun onCreate() {
        super.onCreate()
        Log.i("Init Firebase App","%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%")
        FirebaseApp.initializeApp(context)
        Log.i("Done Firebase App","%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%")

        injectIfNeed()
        registerDownloadNotification()
        registerAdBlockNotification()


        Logger.d(TAG, "onCreate()")
        browserState.isNeedLoad = false
        ErrorReportServer.initialize(this)
        AppData.init(this, moshi, abpDatabase)
        ErrorReportServer.setDetailedLog(AppPrefs.detailed_log.get())
        if (AppPrefs.slow_rendering.get()) {
            WebView.enableSlowWholeDocumentDraw()
        }
        Logger.isDebug = BuildConfig.DEBUG
    }

    override fun androidInjector(): AndroidInjector<Any> {
        injectIfNeed()
        return androidInjector
    }

    private fun injectIfNeed() {
        if (isNeedInject) {
            isNeedInject = false
            DaggerAppComponent.factory().create(this).inject(this)
        }
    }

    override fun attachBaseContext(base: Context) {
        val lang = AppPrefs.language.get()
        val context = if (lang != "") {
            val config = base.createLanguageConfig(lang)
            ContextCompat(base.createConfigurationContext(config), base)
        } else {
            base
        }

        super.attachBaseContext(context)
    }

    private class ContextCompat(
        configContext: Context,
        private val baseActivityContext: Context
    ) : ContextWrapper(configContext) {

        override fun getSystemService(name: String): Any? {
            return baseActivityContext.getSystemService(name)
        }

        override fun getSystemServiceName(serviceClass: Class<*>): String? {
            return baseActivityContext.getSystemServiceName(serviceClass)
        }
    }

    companion object {
        private const val TAG = "YuzuBrowserApplication"
        const val PERMISSION_MYAPP_SIGNATURE = BuildConfig.LIBRARY_PACKAGE_NAME + ".permission.myapp.signature"
        lateinit var instance: YuzuBrowserApplication

        init {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}
