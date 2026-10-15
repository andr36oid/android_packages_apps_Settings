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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;

import java.util.concurrent.atomic.AtomicBoolean;

import com.android.settings.R;

/**
 * Tries out a new peak refresh rate. Some panels can't show every timing in their device
 * tree and go black or garbled, so the new rate only stays when the user picks Keep within
 * TIMEOUT_S seconds. Revert and Back go back at once; otherwise, also when the page was
 * left, the old rate comes back when the time is up.
 * The old rate is written to storage first, so a restart during the trial also goes back
 * (RefreshRateTrialReceiver).
 *
 * A rate the screen can't show stalls the display: nothing gets drawn, and this page's UI
 * thread then waits for frames that never finish. So the time-out runs on a thread of its
 * own, and the old rate is also in persist.andr36oid.refresh_trial_old, which the display
 * service reads at start before it picks any mode (DisplayModeDirector).
 */
final class RefreshRateTrial {

    private static final String TAG = "RefreshRateTrial";
    private static final String PREFS = "andr36oid_refresh_rate_trial";
    private static final String KEY_OLD_RATE = "old_rate";
    private static final int TIMEOUT_S = 15;
    private static final String PROP_OLD_RATE = "persist.andr36oid.refresh_trial_old";
    private static Handler sTimeoutHandler;

    interface Listener {
        /** The trial ended, the rate is now whatever was kept or restored. */
        void onTrialDone();
    }

    private RefreshRateTrial() {}

    static void start(Context context, float oldRate, float newRate, String oldName,
            Listener listener) {
        prefs(context).edit().putFloat(KEY_OLD_RATE, oldRate).commit();
        SystemProperties.set(PROP_OLD_RATE, Float.toString(oldRate));
        final Context app = context.getApplicationContext();
        setRate(context, newRate);

        final Handler handler = new Handler(Looper.getMainLooper());
        final AtomicBoolean kept = new AtomicBoolean();
        final AtomicBoolean done = new AtomicBoolean();
        final int[] left = {TIMEOUT_S};
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.andr36oid_refresh_trial_title)
                .setMessage(message(context, oldName, left[0]))
                .setPositiveButton(R.string.andr36oid_refresh_trial_keep,
                        (d, which) -> kept.set(true))
                .setNegativeButton(R.string.andr36oid_refresh_trial_revert, null)
                .create();
        // From the UI thread or the time-out thread, whichever is first
        final Runnable finish = () -> {
            if (done.getAndSet(true)) {
                return;
            }
            if (!kept.get()) {
                setRate(app, oldRate);
            }
            clear(app);
            BlackFrameEntry.sync(app);
            handler.post(listener::onTrialDone);
        };
        final Runnable timeout = () -> {
            finish.run();
            handler.post(() -> {
                try {
                    dialog.dismiss();
                } catch (RuntimeException e) {
                    Log.w(TAG, "Dialog already gone", e);
                }
            });
        };
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (--left[0] > 0) {
                    dialog.setMessage(message(context, oldName, left[0]));
                    handler.postDelayed(this, 1000);
                    return;
                }
                // Go back even when the page was closed and the dialog went with it
                finish.run();
                try {
                    dialog.dismiss();
                } catch (RuntimeException e) {
                    Log.w(TAG, "Dialog already gone", e);
                }
            }
        };
        dialog.setOnDismissListener((DialogInterface d) -> {
            handler.removeCallbacks(tick);
            timeoutHandler().removeCallbacks(timeout);
            finish.run();
        });
        dialog.show();
        // A blind A press on a garbled screen should go back, not keep it
        final Button revert = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (revert != null) {
            revert.requestFocus();
        }
        handler.postDelayed(tick, 1000);
        timeoutHandler().postDelayed(timeout, TIMEOUT_S * 1000L);
    }

    private static synchronized Handler timeoutHandler() {
        if (sTimeoutHandler == null) {
            final HandlerThread thread = new HandlerThread("RefreshRateTrial");
            thread.start();
            sTimeoutHandler = new Handler(thread.getLooper());
        }
        return sTimeoutHandler;
    }

    /** Puts the old rate back if a trial was still running when the console restarted. */
    static void revertUnfinished(Context context) {
        final SharedPreferences prefs = prefs(context);
        if (!prefs.contains(KEY_OLD_RATE)) {
            return;
        }
        final float oldRate = prefs.getFloat(KEY_OLD_RATE, 60f);
        Log.i(TAG, "Refresh rate trial didn't finish, back to " + oldRate + " Hz");
        setRate(context, oldRate);
        clear(context);
        BlackFrameEntry.sync(context);
    }

    private static CharSequence message(Context context, String oldName, int seconds) {
        return context.getResources().getQuantityString(
                R.plurals.andr36oid_refresh_trial_message, seconds, oldName, seconds);
    }

    private static void setRate(Context context, float rate) {
        Settings.System.putFloat(context.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, rate);
    }

    private static void clear(Context context) {
        SystemProperties.set(PROP_OLD_RATE, "");
        prefs(context).edit().remove(KEY_OLD_RATE).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
