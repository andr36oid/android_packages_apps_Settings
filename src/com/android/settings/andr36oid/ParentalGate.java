package com.android.settings.andr36oid;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;

/**
 * andr36oid: while parental controls are on, Settings asks for their PIN when it is opened.
 * Play time keeps the unlock (Settings.Global andr36oid_parental_unlocked) until the screen
 * goes off or another app comes to the front, so moving around Settings asks only once.
 */
public final class ParentalGate {

    private static final String GLOBAL_ON = "andr36oid_parental_controls";
    private static final String GLOBAL_UNLOCKED = "andr36oid_parental_unlocked";
    private static final int REQUEST_PIN = 0x7a36;
    private static final ComponentName PIN = new ComponentName("org.andr36oid.playtime",
            "org.andr36oid.playtime.PinActivity");

    /** a PIN page is open for one of Settings' pages */
    private static boolean sAsking;

    private ParentalGate() {
    }

    /** From onResume: asks for the PIN if parental controls are on and Settings is locked. */
    public static void check(Activity activity) {
        if (sAsking || activity.isFinishing()) return;
        if (Settings.Global.getInt(activity.getContentResolver(), GLOBAL_ON, 0) == 0
                || Settings.Global.getInt(activity.getContentResolver(), GLOBAL_UNLOCKED, 0)
                        != 0) {
            return;
        }
        sAsking = true;
        try {
            activity.startActivityForResult(new Intent().setComponent(PIN), REQUEST_PIN);
        } catch (RuntimeException e) {
            // Play time is missing: nothing to ask with
            sAsking = false;
        }
    }

    /**
     * From onActivityResult: true if it was the PIN's. Backing out of the PIN closes Settings;
     * if the PIN page was only cleared away (Settings opened again from the quick settings),
     * onResume asks again.
     */
    public static boolean onResult(Activity activity, int requestCode, int resultCode,
            Intent data) {
        if (requestCode != REQUEST_PIN) return false;
        sAsking = false;
        if (resultCode != Activity.RESULT_OK && data != null
                && data.getBooleanExtra("cancelled_by_user", false)) {
            activity.finishAffinity();
        }
        return true;
    }
}
