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

import android.app.SearchManager
import android.content.ContentValues.TAG
import android.content.Intent
import android.net.Uri
import android.os.BadParcelableException
import android.os.Bundle
import android.provider.Browser
import android.speech.RecognizerResultsIntent
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import jp.hazuki.yuzubrowser.browser.BrowserActivity
import jp.hazuki.yuzubrowser.legacy.Constants.intent.EXTRA_OPEN_FROM_YUZU
import jp.hazuki.yuzubrowser.legacy.utils.WebUtils
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import jp.hazuki.yuzubrowser.ui.utils.isUrl
import jp.hazuki.yuzubrowser.ui.utils.makeUrlFromQuery


class HandleIntentActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent != null && intent.action != null) {



            handleIntent(intent)
        }


        finish()
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action

        if(Intent.ACTION_VIEW == action && intent.data!!.host.equals("yuzubrowserme.page.link") && intent.data!!.scheme.equals("https"))
        {
            FirebaseDynamicLinks.getInstance()
                .getDynamicLink(intent)
                .addOnSuccessListener(this) { pendingDynamicLinkData ->
                    // Get deep link from result (may be null if no link is found)
                    var deepLink: Uri? = null
                    if (pendingDynamicLinkData != null) {
                        deepLink = pendingDynamicLinkData.link
                    }

                    try {
                        val openInNewTab = intent.getBooleanExtra(EXTRA_OPEN_FROM_YUZU, false)
                        startBrowser(deepLink.toString(), openInNewTab, openInNewTab)
                    } catch (e: BadParcelableException) {
                        startBrowser(deepLink.toString(), window = false, openInNewTab = false)
                    }

                }
                .addOnFailureListener(this) { e -> Log.w(TAG, "getDynamicLink:onFailure", e) }

            return
        }

        if (Intent.ACTION_VIEW == action) {
            var url = intent.dataString
            if (url.isNullOrEmpty())
                url = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!url.isNullOrEmpty()) {
                try {
                    val openInNewTab = intent.getBooleanExtra(EXTRA_OPEN_FROM_YUZU, false)
                    startBrowser(url, openInNewTab, openInNewTab)
                } catch (e: BadParcelableException) {
                    startBrowser(url, window = false, openInNewTab = false)
                }

                return
            }
        } else if (Intent.ACTION_WEB_SEARCH == action) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            if (query != null) {
                val url = WebUtils.makeSearchUrlFromQuery(query, AppPrefs.search_url.get(), "%s")
                if (url.isNotEmpty()) {
                    startBrowser(url, packageName == intent.getStringExtra(Browser.EXTRA_APPLICATION_ID), false)
                    return
                }
            }
        } else if (Intent.ACTION_SEND == action) {
            val query = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!query.isNullOrEmpty()) {
                if (query.isUrl()) {
                    startBrowser(query, window = false, openInNewTab = false)
                } else {
                    var text = WebUtils.extractionUrl(query)
                    if (query == text) {
                        text = query.makeUrlFromQuery(AppPrefs.search_url.get(), "%s")
                    }
                    startBrowser(text, window = false, openInNewTab = false)
                }
                return
            }
        } else if (RecognizerResultsIntent.ACTION_VOICE_SEARCH_RESULTS == action) {
            val urls = intent.getStringArrayListExtra(RecognizerResultsIntent.EXTRA_VOICE_SEARCH_RESULT_URLS)
            if (!urls.isNullOrEmpty()) {
                startBrowser(urls[0], window = false, openInNewTab = false)
                return
            }
        }

        Toast.makeText(applicationContext, R.string.page_not_found, Toast.LENGTH_SHORT).show()
    }

    private fun startBrowser(url: String, window: Boolean, openInNewTab: Boolean) {
        val send = Intent(this, BrowserActivity::class.java)
        send.action = Intent.ACTION_VIEW
        send.data = Uri.parse(url)
        send.putExtra(BrowserActivity.EXTRA_WINDOW_MODE, window)
        send.putExtra(BrowserActivity.EXTRA_SHOULD_OPEN_IN_NEW_TAB, openInNewTab)
        startActivity(send)
    }
}
