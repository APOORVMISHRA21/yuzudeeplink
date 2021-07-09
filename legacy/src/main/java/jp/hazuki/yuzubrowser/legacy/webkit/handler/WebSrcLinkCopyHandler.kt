/*
 * Copyright (c) 2017 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package jp.hazuki.yuzubrowser.legacy.webkit.handler

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import jp.hazuki.yuzubrowser.legacy.utils.extensions.setClipboardWithToast
import java.lang.ref.WeakReference

class WebSrcLinkCopyHandler(context: Context) : Handler(Looper.getMainLooper()) {
    private val mReference: WeakReference<Context> = WeakReference(context)

    override fun handleMessage(msg: Message) {
        val text = msg.data.getString("title")
        if (!text.isNullOrEmpty()) {
            mReference.get()?.run {
                setClipboardWithToast(text)
            }
        }
    }
}
