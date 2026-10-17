/*
 * Copyright (C) 2026 andr36oid
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

package com.android.settings.deviceinfo.storage;

import android.content.Context;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.utils.ThreadUtils;

/**
 * Storage > "Second SD card": off unmounts a card in the second slot right away and keeps it
 * unmounted (persist.andr36oid.sd2_mount, read by StorageManagerService when a card shows
 * up), and from the next start the kernel doesn't register the slot at all: the Kernel
 * Tweaks helper puts dw_mmc_rockchip.second_slot=0 on the command line for
 * persist.andr36oid.kt.sd2_slot=off. For a slow, damaged card or one only meant for another
 * console. The summary says when a restart is still needed.
 */
public class SecondSdCardPreferenceController extends TogglePreferenceController {
    private static final String TAG = "SecondSdCard";
    private static final String PROP_SD2_MOUNT = "persist.andr36oid.sd2_mount";
    // the Kernel Tweaks start option, and what this start got
    private static final String PROP_SD2_SLOT = "persist.andr36oid.kt.sd2_slot";
    private static final String PROP_SD2_SLOT_NOW = "sys.andr36oid.ktc.sd2_slot";
    private static final String KT_REQUEST_PROP = "sys.andr36oid.kt.request";
    private static final String KT_SERVICE = "andr36oid-ktweaks";
    // the card the system starts from, see StorageManagerService
    private static final String BOOT_CARD_DISK_ID = "disk:179,0";

    private Preference mPreference;

    public SecondSdCardPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public CharSequence getSummary() {
        // "on" when the helper hasn't said: a kernel or console without the switch
        final boolean slotNow = !"off".equals(SystemProperties.get(PROP_SD2_SLOT_NOW));
        if (isChecked() != slotNow) {
            return mContext.getText(isChecked() ? R.string.andr36oid_sd2_restart_on
                    : R.string.andr36oid_sd2_restart_off);
        }
        return mContext.getText(R.string.andr36oid_sd2_mount_summary);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean isChecked() {
        return !"0".equals(SystemProperties.get(PROP_SD2_MOUNT));
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        SystemProperties.set(PROP_SD2_MOUNT, isChecked ? "1" : "0");
        // the kernel part, for the next start: the root helper writes it to BOOT
        SystemProperties.set(PROP_SD2_SLOT, isChecked ? "" : "off");
        SystemProperties.set(KT_REQUEST_PROP, Long.toString(SystemClock.elapsedRealtime()));
        SystemProperties.set("ctl.start", KT_SERVICE);
        if (mPreference != null) refreshSummary(mPreference);
        final StorageManager storage = mContext.getSystemService(StorageManager.class);
        // mounting and unmounting wait for vold, not on the UI thread
        ThreadUtils.postOnBackgroundThread(() -> {
            for (VolumeInfo vol : storage.getVolumes()) {
                final DiskInfo disk = vol.getDisk();
                if (vol.getType() != VolumeInfo.TYPE_PUBLIC || disk == null || !disk.isSd()
                        || BOOT_CARD_DISK_ID.equals(disk.getId())) {
                    continue;
                }
                try {
                    if (isChecked && vol.getState() == VolumeInfo.STATE_UNMOUNTED) {
                        storage.mount(vol.getId());
                    } else if (!isChecked && vol.isMountedReadable()) {
                        storage.unmount(vol.getId());
                    }
                } catch (RuntimeException e) {
                    Log.w(TAG, "Couldn't " + (isChecked ? "mount " : "unmount ") + vol.getId(),
                            e);
                }
            }
        });
        return true;
    }
}
