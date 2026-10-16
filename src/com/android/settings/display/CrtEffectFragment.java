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

import android.app.settings.SettingsEnums;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.widget.SeekBarPreference;

/**
 * Settings > Display > CRT effect. The hardware composer puts a still picture of dark lines
 * on the display controller's top window and the controller blends it in while it sends
 * the picture to the screen, so the graphics chip does no extra work. This page only
 * writes the persist.andr36oid.crt.* properties; the composer reads them every frame, so
 * a change shows as soon as the page redraws (see hardware/rockchip crt_mask.cpp).
 */
public class CrtEffectFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "CrtEffect";

    private static final String PROP_ENABLE = "persist.andr36oid.crt.enable";
    private static final String PROP_STYLE = "persist.andr36oid.crt.style";
    private static final String PROP_STRENGTH = "persist.andr36oid.crt.strength";
    private static final String PROP_PITCH = "persist.andr36oid.crt.pitch";
    private static final String PROP_PHASE = "persist.andr36oid.crt.phase";

    /** the composer's defaults, keep in step with crt_mask.cpp */
    private static final int DEFAULT_STRENGTH = 50;

    private SwitchPreference mEnable;
    private ListPreference mStyle;
    private SeekBarPreference mStrength;
    private ListPreference mPitch;
    private SwitchPreference mPhase;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.andr36oid_crt_effect;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEnable = findPreference("enable");
        mStyle = findPreference("style");
        mStrength = findPreference("strength");
        mPitch = findPreference("pitch");
        mPhase = findPreference("phase");

        mEnable.setOnPreferenceChangeListener(this);
        mStyle.setOnPreferenceChangeListener(this);
        mStrength.setOnPreferenceChangeListener(this);
        mPitch.setOnPreferenceChangeListener(this);
        mPhase.setOnPreferenceChangeListener(this);
        // left and right on the d-pad move the bar, and every step shows on screen at once
        mStrength.setContinuousUpdates(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        final boolean on = SystemProperties.getInt(PROP_ENABLE, 0) == 1;
        mEnable.setChecked(on);
        mStyle.setValue(String.valueOf(SystemProperties.getInt(PROP_STYLE, 0)));
        mStrength.setProgress(SystemProperties.getInt(PROP_STRENGTH, DEFAULT_STRENGTH));
        mPitch.setValue(String.valueOf(SystemProperties.getInt(PROP_PITCH, 0)));
        mPhase.setChecked(SystemProperties.getInt(PROP_PHASE, 0) != 0);
        updateEnabled(on);
    }

    private void updateEnabled(boolean on) {
        mStyle.setEnabled(on);
        mStrength.setEnabled(on);
        mPitch.setEnabled(on);
        mPhase.setEnabled(on);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        if (preference == mEnable) {
            final boolean on = (Boolean) newValue;
            if (on && SystemProperties.get(PROP_STRENGTH, "").isEmpty()) {
                set(PROP_STRENGTH, String.valueOf(DEFAULT_STRENGTH));
            }
            updateEnabled(on);
            return set(PROP_ENABLE, on ? "1" : "0");
        } else if (preference == mStyle) {
            return set(PROP_STYLE, (String) newValue);
        } else if (preference == mStrength) {
            return set(PROP_STRENGTH, String.valueOf((Integer) newValue));
        } else if (preference == mPitch) {
            return set(PROP_PITCH, (String) newValue);
        } else if (preference == mPhase) {
            return set(PROP_PHASE, (Boolean) newValue ? "1" : "0");
        }
        Log.w(TAG, "Unknown row " + key);
        return false;
    }

    private static boolean set(String prop, String value) {
        try {
            SystemProperties.set(prop, value);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Can't set " + prop, e);
            return false;
        }
    }
}
