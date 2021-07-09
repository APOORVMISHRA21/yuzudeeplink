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

package jp.hazuki.yuzubrowser.browser.tab;

import jp.hazuki.yuzubrowser.browser.BrowserActivity;
import jp.hazuki.yuzubrowser.favicon.FaviconManager;
import jp.hazuki.yuzubrowser.legacy.tab.manager.TabManager;
import jp.hazuki.yuzubrowser.webview.WebViewFactory;

public class TabManagerFactory {
    public static TabManager newInstance(BrowserActivity activity, WebViewFactory factory, FaviconManager faviconManager) {
        return new CacheTabManager(activity, factory, faviconManager);
    }
}
