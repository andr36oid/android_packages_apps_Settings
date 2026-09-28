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
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;

/**
 * Extra dim: darker than the lowest backlight level, for playing in bed. The value is the percent
 * of brightness kept; ColorDisplayService (frameworks/base) applies it, in the display hardware
 * when it can. Shown when the build has that part (ro.andr36oid.extra_dim).
 */
public class ExtraDimPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {

    static final String SETTING = "andr36oid_extra_dim";

    public ExtraDimPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return SystemProperties.getBoolean("ro.andr36oid.extra_dim", false)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void updateState(Preference preference) {
        final ListPreference list = (ListPreference) preference;
        final int value = Settings.Secure.getInt(mContext.getContentResolver(), SETTING, 0);
        list.setValue(String.valueOf(value));
        if (list.getValue() == null) {
            // Set by hand to a level the list doesn't offer
            list.setValue("0");
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
        Settings.Secure.putInt(mContext.getContentResolver(), SETTING, value);
        final ListPreference list = (ListPreference) preference;
        list.setValue((String) newValue);
        list.setSummary(list.getEntry());
        return true;
    }
}
