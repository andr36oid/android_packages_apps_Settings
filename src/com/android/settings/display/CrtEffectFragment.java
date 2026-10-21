/*
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
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

/**
 * Settings > Display > CRT effect (experimental). Two parts, both switched by "Use CRT effect":
 * <ul>
 * <li>Scanlines and mask: the hardware composer puts a still picture of dark lines on the
 * display controller's top window and the controller blends it in while it sends the
 * picture to the screen; no graphics chip work (hardware/rockchip crt_mask.cpp).</li>
 * <li>Curvature, glow and phosphor mask: SurfaceFlinger draws every frame on the graphics
 * chip and runs one extra pass over it (frameworks/native CrtFilter). Costly, so each is
 * marked as slowing games down.</li>
 * </ul>
 * This page only writes persist.andr36oid.crt.*; both readers pick changes up on the next
 * frame, so the page itself shows them.
 */
public class CrtEffectFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "CrtEffect";

    private static final String PROP_ENABLE = "persist.andr36oid.crt.enable";
    private static final String PROP_LINES = "persist.andr36oid.crt.lines";
    private static final String PROP_STYLE = "persist.andr36oid.crt.style";
    private static final String PROP_STRENGTH = "persist.andr36oid.crt.strength";
    private static final String PROP_PITCH = "persist.andr36oid.crt.pitch";
    private static final String PROP_PHASE = "persist.andr36oid.crt.phase";
    private static final String PROP_CURVATURE = "persist.andr36oid.crt.curvature";
    private static final String PROP_GLOW = "persist.andr36oid.crt.glow";
    private static final String PROP_GPU_MASK = "persist.andr36oid.crt.gpu_mask";

    /** the switch-off animation, read by ColorFade when the screen goes off */
    private static final String PROP_OFF = "persist.andr36oid.crtoff.";
    private static final int OFF_NONE = 0;
    private static final int OFF_CRT = 2;
    /** list rows and their ColorFade defaults */
    private static final String[] OFF_LISTS = {"style", "length", "shape", "tint"};
    private static final int[] OFF_LIST_DEFAULTS = {OFF_NONE, 700, 0, 0};
    /** sliders and their ColorFade defaults */
    private static final String[] OFF_SLIDERS = {"flash", "afterglow", "tear", "split", "noise",
            "roll", "flicker"};
    private static final int[] OFF_SLIDER_DEFAULTS = {70, 50, 25, 30, 20, 0, 25};

    /** the readers' defaults, keep in step with crt_mask.cpp and SurfaceFlinger.cpp */
    private static final int DEFAULT_STRENGTH = 50;
    private static final int MIN_STRENGTH = 10;
    /** d-pad step on every slider */
    private static final int STEP = 5;

    private SwitchPreference mEnable;
    private SwitchPreference mLines;
    private ListPreference mStyle;
    private SeekBarPreference mStrength;
    private ListPreference mPitch;
    private SwitchPreference mPhase;
    private SeekBarPreference mCurvature;
    private SeekBarPreference mGlow;
    private SeekBarPreference mGpuMask;
    private final ListPreference[] mOffLists = new ListPreference[OFF_LISTS.length];
    private final SeekBarPreference[] mOffSliders = new SeekBarPreference[OFF_SLIDERS.length];
    private Preference mOffGlitchNote;
    private Preference mOffTry;

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
        mLines = findPreference("lines");
        mStyle = findPreference("style");
        mStrength = findPreference("strength");
        mPitch = findPreference("pitch");
        mPhase = findPreference("phase");
        mCurvature = findPreference("curvature");
        mGlow = findPreference("glow");
        mGpuMask = findPreference("gpu_mask");

        setUpSlider(mStrength, MIN_STRENGTH);
        setUpSlider(mCurvature, 0);
        setUpSlider(mGlow, 0);
        setUpSlider(mGpuMask, 0);
        for (Preference p : new Preference[] {mEnable, mLines, mStyle, mPitch, mPhase}) {
            p.setOnPreferenceChangeListener(this);
        }

        for (int i = 0; i < OFF_LISTS.length; i++) {
            mOffLists[i] = findPreference("off_" + OFF_LISTS[i]);
            mOffLists[i].setOnPreferenceChangeListener(this);
        }
        for (int i = 0; i < OFF_SLIDERS.length; i++) {
            mOffSliders[i] = findPreference("off_" + OFF_SLIDERS[i]);
            setUpSlider(mOffSliders[i], 0);
        }
        mOffGlitchNote = findPreference("off_glitch_note");
        mOffTry = findPreference("off_try");
        mOffTry.setOnPreferenceClickListener(p -> {
            // the same as pressing power, so the animation plays as it will
            getContext().getSystemService(PowerManager.class)
                    .goToSleep(SystemClock.uptimeMillis());
            return true;
        });
    }

    /** Left and right on the d-pad move the bar, and every step shows on screen at once. */
    private void setUpSlider(SeekBarPreference slider, int min) {
        slider.setMin(min);
        slider.setMax(100);
        slider.setSeekBarIncrement(STEP);
        slider.setAdjustable(true);
        slider.setShowSeekBarValue(true);
        slider.setUpdatesContinuously(true);
        slider.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mEnable.setChecked(isEnabled());
        mLines.setChecked(SystemProperties.getInt(PROP_LINES, 1) == 1);
        mStyle.setValue(String.valueOf(SystemProperties.getInt(PROP_STYLE, 0)));
        mStrength.setValue(SystemProperties.getInt(PROP_STRENGTH, DEFAULT_STRENGTH));
        mPitch.setValue(String.valueOf(SystemProperties.getInt(PROP_PITCH, 0)));
        mPhase.setChecked(SystemProperties.getInt(PROP_PHASE, 0) != 0);
        mCurvature.setValue(SystemProperties.getInt(PROP_CURVATURE, 0));
        mGlow.setValue(SystemProperties.getInt(PROP_GLOW, 0));
        mGpuMask.setValue(SystemProperties.getInt(PROP_GPU_MASK, 0));
        updateEnabled(mEnable.isChecked(), mLines.isChecked());
        for (int i = 0; i < OFF_LISTS.length; i++) {
            mOffLists[i].setValue(String.valueOf(
                    SystemProperties.getInt(PROP_OFF + OFF_LISTS[i], OFF_LIST_DEFAULTS[i])));
        }
        for (int i = 0; i < OFF_SLIDERS.length; i++) {
            mOffSliders[i].setValue(
                    SystemProperties.getInt(PROP_OFF + OFF_SLIDERS[i], OFF_SLIDER_DEFAULTS[i]));
        }
        updateOffEnabled(SystemProperties.getInt(PROP_OFF + "style", OFF_NONE));
    }

    /** Length and Try it for any animation, the rest only for the old TV. */
    private void updateOffEnabled(int style) {
        mOffLists[1].setEnabled(style != OFF_NONE);
        mOffTry.setEnabled(style != OFF_NONE);
        for (int i = 2; i < mOffLists.length; i++) mOffLists[i].setEnabled(style == OFF_CRT);
        for (SeekBarPreference s : mOffSliders) s.setEnabled(style == OFF_CRT);
        mOffGlitchNote.setEnabled(style == OFF_CRT);
    }

    private void updateEnabled(boolean on, boolean lines) {
        mLines.setEnabled(on);
        mStyle.setEnabled(on && lines);
        mStrength.setEnabled(on && lines);
        mPitch.setEnabled(on && lines);
        mPhase.setEnabled(on && lines);
        mCurvature.setEnabled(on);
        mGlow.setEnabled(on);
        mGpuMask.setEnabled(on);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mEnable) {
            final boolean on = (Boolean) newValue;
            updateEnabled(on, mLines.isChecked());
            return setEnabled(getContext(), on);
        } else if (preference == mLines) {
            final boolean lines = (Boolean) newValue;
            updateEnabled(mEnable.isChecked(), lines);
            return set(PROP_LINES, lines ? "1" : "0");
        } else if (preference == mStyle) {
            return set(PROP_STYLE, (String) newValue);
        } else if (preference == mStrength) {
            return set(PROP_STRENGTH, String.valueOf((Integer) newValue));
        } else if (preference == mPitch) {
            return set(PROP_PITCH, (String) newValue);
        } else if (preference == mPhase) {
            return set(PROP_PHASE, (Boolean) newValue ? "1" : "0");
        } else if (preference == mCurvature) {
            return set(PROP_CURVATURE, String.valueOf((Integer) newValue));
        } else if (preference == mGlow) {
            return set(PROP_GLOW, String.valueOf((Integer) newValue));
        } else if (preference == mGpuMask) {
            return set(PROP_GPU_MASK, String.valueOf((Integer) newValue));
        }
        for (int i = 0; i < OFF_LISTS.length; i++) {
            if (preference == mOffLists[i]) {
                if (i == 0) updateOffEnabled(Integer.parseInt((String) newValue));
                return set(PROP_OFF + OFF_LISTS[i], (String) newValue);
            }
        }
        for (int i = 0; i < OFF_SLIDERS.length; i++) {
            if (preference == mOffSliders[i]) {
                return set(PROP_OFF + OFF_SLIDERS[i], String.valueOf((Integer) newValue));
            }
        }
        Log.w(TAG, "Unknown row " + preference.getKey());
        return false;
    }

    static boolean isEnabled() {
        return SystemProperties.getInt(PROP_ENABLE, 0) == 1;
    }

    /** Turns the effect on or off, also from the Quick Settings tile (CrtTileService). */
    static boolean setEnabled(Context context, boolean on) {
        if (on && SystemProperties.get(PROP_STRENGTH, "").isEmpty()) {
            set(PROP_STRENGTH, String.valueOf(DEFAULT_STRENGTH));
        }
        if (!set(PROP_ENABLE, on ? "1" : "0")) {
            return false;
        }
        if (on) {
            CrtTileService.addOnce(context);
        }
        return true;
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
