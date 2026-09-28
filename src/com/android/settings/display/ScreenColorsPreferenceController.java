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

import android.content.Context;
import android.os.SystemProperties;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;

/**
 * Screen colors: Natural / Vivid / Extra vivid. The Rockchip composer passes
 * persist.vendor.saturation.main (50 = unchanged) to the display controller's saturation
 * stage (VOP BCSH), so it costs nothing per frame. The composer rereads it when
 * vendor.display.timeline changes. Shown when the build enables it (ro.andr36oid.screen_colors).
 */
public class ScreenColorsPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {

    static final String SATURATION = "persist.vendor.saturation.main";
    static final String TIMELINE = "vendor.display.timeline";
    static final int NATURAL = 50;

    public ScreenColorsPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return SystemProperties.getBoolean("ro.andr36oid.screen_colors", false)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void updateState(Preference preference) {
        final ListPreference list = (ListPreference) preference;
        list.setValue(String.valueOf(SystemProperties.getInt(SATURATION, NATURAL)));
        if (list.getValue() == null) {
            list.setValue(String.valueOf(NATURAL));
        }
        list.setSummary(list.getEntry());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final int value;
        try {
            value = Integer.parseInt((String) newValue);
        } catch (NumberFormatException e) {
            return false;
        }
        if (value < 0 || value > 100) {
            return false;
        }
        SystemProperties.set(SATURATION, String.valueOf(value));
        // Any new value makes the composer apply it on its next frame. 0 would make it
        // reapply on every frame, so skip it.
        int timeline = SystemProperties.getInt(TIMELINE, 0) + 1;
        if (timeline <= 0) {
            timeline = 1;
        }
        SystemProperties.set(TIMELINE, String.valueOf(timeline));
        final ListPreference list = (ListPreference) preference;
        list.setValue((String) newValue);
        list.setSummary(list.getEntry());
        return true;
    }
}
