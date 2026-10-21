package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;
import android.os.SystemProperties;

import com.android.settings.core.BasePreferenceController;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** andr36oid: the build as in the image's name (system's build time), 20261021-2216. */
public class Andr36oidBuildPreferenceController extends BasePreferenceController {

    public Andr36oidBuildPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return SystemProperties.getLong("ro.build.date.utc", 0) > 0
                && !SystemProperties.get("ro.andr36oid.version").isEmpty()
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        final SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(SystemProperties.getLong("ro.build.date.utc", 0) * 1000));
    }
}
