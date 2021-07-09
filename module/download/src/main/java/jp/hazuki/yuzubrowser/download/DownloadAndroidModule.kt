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

package jp.hazuki.yuzubrowser.download

import dagger.Module
import dagger.android.ContributesAndroidInjector
import jp.hazuki.yuzubrowser.download.service.DownloadService
import jp.hazuki.yuzubrowser.download.ui.DownloadListActivity
import jp.hazuki.yuzubrowser.download.ui.FastDownloadActivity
import jp.hazuki.yuzubrowser.download.ui.fragment.DownloadListFragment

@Module
abstract class DownloadAndroidModule {

    @ContributesAndroidInjector
    abstract fun contributeDownloadService(): DownloadService

    @ContributesAndroidInjector
    abstract fun contributeFastDownloadActivity(): FastDownloadActivity

    @ContributesAndroidInjector
    abstract fun contributeDownloadListActivity(): DownloadListActivity

    @ContributesAndroidInjector
    abstract fun contributeDownloadListFragment(): DownloadListFragment
}
