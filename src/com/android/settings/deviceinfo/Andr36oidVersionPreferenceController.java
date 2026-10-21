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

package com.android.settings.deviceinfo;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;

/**
 * Shows the andr36oid release the console runs, e.g. v2026-09-26-release. Tapping it rolls
 * the credits, which have no entry of their own.
 */
public class Andr36oidVersionPreferenceController extends BasePreferenceController {

    static final String PROPERTY = "ro.andr36oid.version";

    private static final Intent CREDITS = new Intent().setClassName(
            "org.andr36oid.credits", "org.andr36oid.credits.CreditsActivity");

    public Andr36oidVersionPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return TextUtils.isEmpty(SystemProperties.get(PROPERTY))
                ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return SystemProperties.get(PROPERTY);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!getPreferenceKey().equals(preference.getKey())) {
            return false;
        }
        try {
            preference.getContext().startActivity(CREDITS);
        } catch (ActivityNotFoundException e) {
            // Built without the credits
        }
        return true;
    }
}
