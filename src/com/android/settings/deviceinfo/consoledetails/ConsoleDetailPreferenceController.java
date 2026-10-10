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

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * One row of Settings > About console > Console details. Every row uses this controller; the
 * key says which value it shows. A row whose value can't be read is hidden.
 *
 * Board, board ID, DTB folder and U-Boot come from andr36oid.info.* properties, which the DTB
 * hint service at boot sets (Settings can't read the board ID, BOOT or the U-Boot partition
 * itself). The rest is read here from /proc, /sys and the display.
 */
public class ConsoleDetailPreferenceController extends BasePreferenceController {

    private static final String TAG = "ConsoleDetails";

    static final String KEY_CONSOLE = "console_details_console";
    static final String KEY_BOARD = "console_details_board";
    static final String KEY_BOARD_ID = "console_details_board_id";
    static final String KEY_DTB_FOLDER = "console_details_dtb_folder";
    static final String KEY_SCREEN = "console_details_screen";
    static final String KEY_PROCESSOR = "console_details_processor";
    static final String KEY_MEMORY = "console_details_memory";
    static final String KEY_CARD = "console_details_card";
    static final String KEY_KERNEL = "console_details_kernel";
    static final String KEY_UBOOT = "console_details_uboot";

    private static final String INFO = "andr36oid.info.";

    public ConsoleDetailPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return TextUtils.isEmpty(value()) ? UNSUPPORTED_ON_DEVICE : AVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return value();
    }

    private String value() {
        try {
            switch (getPreferenceKey()) {
                case KEY_CONSOLE:
                    return read("/proc/device-tree/model");
                case KEY_BOARD:
                    return board(SystemProperties.get(INFO + "board"));
                case KEY_BOARD_ID:
                    return SystemProperties.get(INFO + "board_id");
                case KEY_DTB_FOLDER:
                    return dtbFolder(SystemProperties.get(INFO + "panel"));
                case KEY_SCREEN:
                    return screen();
                case KEY_PROCESSOR:
                    return processor();
                case KEY_MEMORY:
                    return memory();
                case KEY_CARD:
                    return card();
                case KEY_KERNEL:
                    return System.getProperty("os.version", "");
                case KEY_UBOOT:
                    return SystemProperties.get(INFO + "uboot");
                default:
                    return "";
            }
        } catch (RuntimeException e) {
            // A value that can't be read hides its row, it never closes the page
            Log.w(TAG, "can't read " + getPreferenceKey(), e);
            return "";
        }
    }

    private String board(String board) {
        switch (board) {
            case "genuine":
                return mContext.getString(R.string.andr36oid_console_details_board_genuine);
            case "ee":
                return mContext.getString(R.string.andr36oid_console_details_board_ee);
            case "soy":
                return mContext.getString(R.string.andr36oid_console_details_board_soy);
            case "unknown":
                return mContext.getString(R.string.andr36oid_console_details_board_unknown);
            default:
                // Not set: R46H and R50S, or the check hasn't run yet
                return "";
        }
    }

    private String dtbFolder(String folder) {
        if ("custom".equals(folder)) {
            return mContext.getString(R.string.andr36oid_console_details_dtb_custom);
        }
        return folder;
    }

    private String screen() {
        final DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        final Display display = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return "";
        }
        final Display.Mode mode = display.getMode();
        return mContext.getString(R.string.andr36oid_console_details_screen_value,
                mode.getPhysicalWidth(), mode.getPhysicalHeight(),
                String.format(Locale.getDefault(), "%.2f", mode.getRefreshRate()));
    }

    private String processor() {
        final String platform = SystemProperties.get("ro.board.platform");
        if (platform.isEmpty()) {
            return "";
        }
        int cores = 0;
        long maxKhz = 0;
        final File[] cpus = new File("/sys/devices/system/cpu").listFiles();
        if (cpus != null) {
            for (File cpu : cpus) {
                if (!cpu.getName().matches("cpu[0-9]+")) {
                    continue;
                }
                cores++;
                maxKhz = Math.max(maxKhz,
                        parseLong(read(cpu.getPath() + "/cpufreq/cpuinfo_max_freq")));
            }
        }
        final String chip = "Rockchip " + platform.toUpperCase(Locale.ROOT);
        if (cores == 0 || maxKhz == 0) {
            return chip;
        }
        return mContext.getString(R.string.andr36oid_console_details_processor_value, chip,
                cores, String.format(Locale.getDefault(), "%.1f", maxKhz / 1e6));
    }

    private String memory() {
        // MemTotal leaves out what the kernel keeps for itself: round up to the chip size
        long kb = 0;
        for (String line : read("/proc/meminfo").split("\n")) {
            if (line.startsWith("MemTotal:")) {
                kb = parseLong(line.replaceAll("[^0-9]", ""));
                break;
            }
        }
        if (kb == 0) {
            return "";
        }
        long mb = 256;
        while (mb * 1024 < kb) {
            mb *= 2;
        }
        return mb >= 1024 ? (mb / 1024) + " GB" : mb + " MB";
    }

    /** The card Android runs from: the one with the userdata partition. */
    private String card() {
        final File disk = systemDisk();
        if (disk == null) {
            return "";
        }
        final long bytes = parseLong(read(disk.getPath() + "/size")) * 512;
        final String name = read(disk.getPath() + "/device/name");
        final String maker = maker(read(disk.getPath() + "/device/manfid"));
        final String date = read(disk.getPath() + "/device/date");
        final StringBuilder card = new StringBuilder();
        if (!maker.isEmpty()) {
            card.append(maker).append(' ');
        }
        card.append(name.isEmpty() ? disk.getName() : name);
        if (bytes > 0) {
            card.append(", ").append(android.text.format.Formatter.formatShortFileSize(
                    mContext, bytes));
        }
        if (!date.isEmpty()) {
            card.append(", ").append(mContext.getString(
                    R.string.andr36oid_console_details_card_made, date));
        }
        return card.toString();
    }

    private static File systemDisk() {
        final File[] blocks = new File("/sys/class/block").listFiles();
        if (blocks != null) {
            for (File block : blocks) {
                if (read(block.getPath() + "/uevent").contains("PARTNAME=userdata")) {
                    try {
                        // A partition sits in its disk's folder in sysfs
                        return block.getCanonicalFile().getParentFile();
                    } catch (IOException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /** Makers whose SD card ID is well known; others show only the card's own name. */
    private static String maker(String manfid) {
        switch ((int) parseLong(manfid.replaceFirst("^0x", ""), 16)) {
            case 0x02:
                return "Kioxia";
            case 0x03:
                return "SanDisk";
            case 0x1b:
                return "Samsung";
            case 0x28:
                return "Lexar";
            case 0x74:
                return "Transcend";
            default:
                return "";
        }
    }

    private static long parseLong(String s) {
        return parseLong(s, 10);
    }

    private static long parseLong(String s, int radix) {
        try {
            return Long.parseLong(s.trim(), radix);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The file's text without trailing NULs and line ends, or "" when it can't be read. */
    private static String read(String path) {
        try {
            final String s = new String(Files.readAllBytes(new File(path).toPath()),
                    StandardCharsets.UTF_8);
            return s.replaceAll("[\\u0000\\s]+$", "");
        } catch (IOException | SecurityException e) {
            return "";
        }
    }
}
