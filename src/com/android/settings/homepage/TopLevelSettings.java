/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.homepage;

import static com.android.settings.search.actionbar.SearchMenuController.NEED_SEARCH_ICON_IN_ACTION_BAR;
import static com.android.settingslib.search.SearchIndexable.MOBILE;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.support.SupportPreferenceController;
import com.android.settingslib.Utils;
import com.android.settingslib.core.instrumentation.Instrumentable;
import com.android.settingslib.search.SearchIndexable;

import java.util.Arrays;
import java.util.List;

@SearchIndexable(forTarget = MOBILE)
public class TopLevelSettings extends DashboardFragment implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final String TAG = "TopLevelSettings";

    // The console's own entries, drawn on a tinted backdrop so they stand out
    private static final List<String> CONSOLE_KEYS = Arrays.asList(
            "top_level_quick_start", "top_level_button_mapping", "top_level_joystick_mouse",
            "top_level_cpu_overclock", "top_level_usb_mode", "top_level_controller_test",
            "top_level_perf_overlay", "top_level_play_time");

    public TopLevelSettings() {
        final Bundle args = new Bundle();
        // Disable the search icon because this page uses a full search view in actionbar.
        args.putBoolean(NEED_SEARCH_ICON_IN_ACTION_BAR, false);
        setArguments(args);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.top_level_settings;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DASHBOARD_SUMMARY;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(SupportPreferenceController.class).setActivity(getActivity());
    }

    @Override
    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        final RecyclerView recyclerView = super.onCreateRecyclerView(inflater, parent,
                savedInstanceState);
        // The list sits inside the homepage scroll view, which jumps to it whenever the list
        // itself takes focus. Entering touch mode (a mouse click) with an entry focused would
        // hand focus to the list, so let the focus drop instead.
        recyclerView.setFocusableInTouchMode(false);
        recyclerView.addItemDecoration(new ConsoleEntryBackdrop(recyclerView.getContext()));
        return recyclerView;
    }

    @Override
    public int getHelpResource() {
        // Disable the help icon because this page uses a full search view in actionbar.
        return 0;
    }

    @Override
    public Fragment getCallbackFragment() {
        return this;
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        new SubSettingLauncher(getActivity())
                .setDestination(pref.getFragment())
                .setArguments(pref.getExtras())
                .setSourceMetricsCategory(caller instanceof Instrumentable
                        ? ((Instrumentable) caller).getMetricsCategory()
                        : Instrumentable.METRICS_CATEGORY_UNKNOWN)
                .setTitleRes(-1)
                .launch();
        return true;
    }

    @Override
    protected boolean shouldForceRoundedIcon() {
        return getContext().getResources()
                .getBoolean(R.bool.config_force_rounded_icon_TopLevelSettings);
    }

    /** Draws a rounded, lightly tinted backdrop behind the console's own entries. */
    private static class ConsoleEntryBackdrop extends RecyclerView.ItemDecoration {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private final float mInsetX;
        private final float mInsetY;
        private final float mRadius;

        ConsoleEntryBackdrop(Context context) {
            final int accent = Utils.getColorAccentDefaultColor(context);
            mPaint.setColor((accent & 0x00FFFFFF) | 0x26000000);
            final float density = context.getResources().getDisplayMetrics().density;
            mInsetX = 8 * density;
            mInsetY = 2 * density;
            mRadius = 12 * density;
        }

        @Override
        public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
            if (!(parent.getAdapter() instanceof PreferenceGroupAdapter)) {
                return;
            }
            final PreferenceGroupAdapter adapter = (PreferenceGroupAdapter) parent.getAdapter();
            for (int i = 0; i < parent.getChildCount(); i++) {
                final View child = parent.getChildAt(i);
                final int position = parent.getChildAdapterPosition(child);
                if (position == RecyclerView.NO_POSITION) {
                    continue;
                }
                final Preference preference = adapter.getItem(position);
                if (preference == null || !CONSOLE_KEYS.contains(preference.getKey())) {
                    continue;
                }
                mRect.set(child.getLeft() + mInsetX, child.getTop() + mInsetY,
                        child.getRight() - mInsetX, child.getBottom() - mInsetY);
                mRect.offset(child.getTranslationX(), child.getTranslationY());
                canvas.drawRoundRect(mRect, mRadius, mRadius, mPaint);
            }
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.top_level_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    // Never searchable, all entries in this page are already indexed elsewhere.
                    return false;
                }
            };
}
