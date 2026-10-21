package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;
import android.os.SystemProperties;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/** andr36oid: whether this start came from the SD card or the built-in eMMC. */
public class Andr36oidStartedFromPreferenceController extends BasePreferenceController {

    public Andr36oidStartedFromPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return SystemProperties.get("ro.andr36oid.version").isEmpty()
                ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        // an eMMC install's fstab is fstab.emmc or fstab.emmcsd (see boot.ini)
        return mContext.getString(SystemProperties.get("ro.boot.fstab_suffix").startsWith("emmc")
                ? R.string.andr36oid_started_from_emmc : R.string.andr36oid_started_from_sd);
    }
}
