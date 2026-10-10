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

package com.android.settings.deviceinfo.consoledetails;

import android.app.settings.SettingsEnums;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;

/**
 * Settings > About console > Console details: the board, screen, chip, memory, card, kernel
 * and U-Boot of this console, for problem reports and for picking the right DTB.
 */
public class ConsoleDetailsFragment extends DashboardFragment {

    private static final String TAG = "ConsoleDetails";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DIALOG_SETTINGS_HARDWARE_INFO;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.andr36oid_console_details;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }
}
