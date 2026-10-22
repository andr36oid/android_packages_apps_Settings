package com.android.settings.fuelgauge;

import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;

import com.android.settings.core.TogglePreferenceController;

/** The small voltage advisory drawn by SystemUI over apps. */
public class LowVoltageWarningPreferenceController extends TogglePreferenceController {

    private static final String SETTING = "andr36oid_low_voltage_warning";

    public LowVoltageWarningPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return !SystemProperties.get("ro.andr36oid.version").isEmpty()
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean isChecked() {
        return Settings.Secure.getInt(mContext.getContentResolver(), SETTING, 1) != 0;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.Secure.putInt(mContext.getContentResolver(), SETTING, isChecked ? 1 : 0);
    }
}
