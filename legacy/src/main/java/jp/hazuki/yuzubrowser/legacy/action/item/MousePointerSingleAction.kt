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

package jp.hazuki.yuzubrowser.legacy.action.item

import android.app.AlertDialog
import android.os.Parcel
import android.os.Parcelable
import android.view.View
import android.widget.CheckBox
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.action.SingleAction
import jp.hazuki.yuzubrowser.legacy.action.view.ActionActivity
import jp.hazuki.yuzubrowser.ui.app.StartActivityInfo
import java.io.IOException

class MousePointerSingleAction : SingleAction, Parcelable {
    var isBackFinish = true
        private set

    @Throws(IOException::class)
    constructor(id: Int, reader: JsonReader?) : super(id) {
        if (reader != null) {
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.peek() != JsonReader.Token.NAME) return
                when (reader.nextName()) {
                    FIELD_NAME_BACK_FINISH -> {
                        if (reader.peek() == JsonReader.Token.NULL) {
                            reader.skipValue()
                        } else {
                            isBackFinish = reader.nextBoolean()
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
    }

    @Throws(IOException::class)
    override fun writeIdAndData(writer: JsonWriter) {
        writer.value(id)
        writer.beginObject()
        writer.name(FIELD_NAME_BACK_FINISH)
        writer.value(isBackFinish)
        writer.endObject()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(id)
        dest.writeInt(if (isBackFinish) 1 else 0)
    }

    private constructor(source: Parcel) : super(source.readInt()) {
        isBackFinish = source.readInt() == 1
    }

    override fun showSubPreference(context: ActionActivity): StartActivityInfo? {
        val view = View.inflate(context, R.layout.action_checkbox, null)
        val checkBox = view.findViewById(R.id.checkBox) as CheckBox
        checkBox.setText(R.string.action_mousepointer_backfinish)
        checkBox.isChecked = isBackFinish

        AlertDialog.Builder(context)
                .setTitle(R.string.action_settings)
                .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ -> isBackFinish = checkBox.isChecked }
                .setNegativeButton(android.R.string.cancel, null)
                .show()

        return null
    }

    companion object {
        private const val TAG = "MousePointerSingleAction"
        private const val FIELD_NAME_BACK_FINISH = "0"

        @JvmField
        val CREATOR: Parcelable.Creator<MousePointerSingleAction> = object : Parcelable.Creator<MousePointerSingleAction> {
            override fun createFromParcel(source: Parcel): MousePointerSingleAction {
                return MousePointerSingleAction(source)
            }

            override fun newArray(size: Int): Array<MousePointerSingleAction?> {
                return arrayOfNulls(size)
            }
        }
    }

}
