package jp.hazuki.yuzubrowser.legacy.settings.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.Preference;
import jp.hazuki.yuzubrowser.legacy.R;
import jp.hazuki.yuzubrowser.legacy.action.ActionNameArray;
import jp.hazuki.yuzubrowser.legacy.action.view.ActionActivity;

public class ActionPreference extends Preference {
    private final int mActionId;
    private final int mActionType;
    private final int mAddActionList;
    private final int mAddActionValues;
    private final String mTitle;

    public ActionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ActionListPreference);
        mActionType = a.getInt(R.styleable.ActionListPreference_actionGroup, 0);
        mActionId = a.getInt(R.styleable.ActionListPreference_actionId, 0);
        mTitle = a.getString(R.styleable.ActionListPreference_android_title);
        mAddActionList = a.getResourceId(R.styleable.ActionListPreference_android_entries, 0);
        mAddActionValues = a.getResourceId(R.styleable.ActionListPreference_android_entryValues, 0);

        if (mActionType == 0)
            throw new IllegalArgumentException("mActionType is zero");

        if (mActionId == 0)
            throw new IllegalArgumentException("actionId is zero");

        a.recycle();
    }

    @Override
    protected void onClick() {
        super.onClick();

        ActionActivity.Builder builder = new ActionActivity.Builder(getContext())
                .setTitle(mTitle)
                .setActionManager(mActionType, mActionId);
        if (mAddActionList != 0 && mAddActionValues != 0)
            builder.setActionNameArray(new ActionNameArray(getContext(), mAddActionList, mAddActionValues));
        builder.show();
    }
}
