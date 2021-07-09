/*
 * Copyright (C) 2017-2020 Hazuki
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

package jp.hazuki.yuzubrowser.legacy.settings.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import jp.hazuki.yuzubrowser.legacy.Constants
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.help.BROWSER_HELP_URL

class MainSettingsFragment : YuzuPreferenceFragment() {

    override fun onCreateYuzuPreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_main)

        findPreference<Preference>("fragment_browser")!!.setOnPreferenceClickListener {
            openFragment(BrowserSettingsFragment())
            true
        }
        findPreference<Preference>("fragment_page")!!.setOnPreferenceClickListener {
            openFragment(PageSettingFragment())
            true
        }
        findPreference<Preference>("fragment_privacy")!!.setOnPreferenceClickListener {
            openFragment(PrivacyFragment())
            true
        }
        findPreference<Preference>("fragment_ui")!!.setOnPreferenceClickListener {
            openFragment(UiSettingFragment())
            true
        }
        findPreference<Preference>("fragment_action")!!.setOnPreferenceClickListener {
            openFragment(ActionSettingsFragment())
            true
        }
        findPreference<Preference>("fragment_speseddial")!!.setOnPreferenceClickListener {
            openFragment(SpeeddialFragment())
            true
        }
        findPreference<Preference>("fragment_application")!!.setOnPreferenceClickListener {
            openFragment(ApplicationSettingsFragment())
            true
        }
        findPreference<Preference>("fragment_import_export")!!.setOnPreferenceClickListener {
            openFragment(ImportExportFragment())
            true
        }
        findPreference<Preference>("activity_help")!!.setOnPreferenceClickListener {
            val activity = activity ?: return@setOnPreferenceClickListener true
            val intent = Intent(Constants.intent.ACTION_OPEN_DEFAULT)
            intent.setClassName(activity, Constants.activity.MAIN_BROWSER)
            intent.data = Uri.parse(BROWSER_HELP_URL)
            startActivity(intent)
            true
        }
        findPreference<Preference>("fragment_about")!!.setOnPreferenceClickListener {
            openFragment(AboutFragment())
            true
        }
    }
}
