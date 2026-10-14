/*
 * Copyright (C) 2026 The andr36oid Project
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

package com.android.settings.display;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.settings.R;

/**
 * Settings > Display > Black frame insertion (the BlackFrameInsertion app) only shows while
 * Peak refresh rate is 100 Hz or more; below that the black frames would only flicker.
 */
final class BlackFrameEntry {

    private static final String TAG = "BlackFrameEntry";
    private static final String PACKAGE = "org.andr36oid.bfi";
    private static final ComponentName ENTRY =
            new ComponentName(PACKAGE, PACKAGE + ".BfiActivity");
    private static final float MIN_HZ = 99.5f;

    private BlackFrameEntry() {}

    /** Shows or hides the entry for the current peak rate; a toast when it appears. */
    static void sync(Context context) {
        final float rate = Settings.System.getFloat(context.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, 60f);
        final boolean show = rate >= MIN_HZ;
        final PackageManager pm = context.getPackageManager();
        final int state;
        try {
            state = pm.getComponentEnabledSetting(ENTRY);
        } catch (IllegalArgumentException e) {
            return; // app not in this build
        }
        final boolean shown = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        if (show == shown) {
            return;
        }
        try {
            pm.setComponentEnabledSetting(ENTRY, show
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (RuntimeException e) {
            Log.w(TAG, "Can't switch the entry", e);
            return;
        }
        if (show) {
            Toast.makeText(context, R.string.andr36oid_bfi_unlocked, Toast.LENGTH_LONG).show();
        } else {
            // the black frames would only flicker now
            context.stopService(new Intent().setClassName(PACKAGE, PACKAGE + ".BfiService"));
        }
    }
}
