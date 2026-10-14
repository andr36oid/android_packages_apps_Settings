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
import android.app.settings.SettingsEnums;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Settings > Display > Panel overclocking: extra refresh rates to try. The rates are saved
 * in persist.andr36oid.kt.extra_rates (a comma list of Hz, empty for none); the Kernel
 * Tweaks helper puts them on the kernel command line for the next start
 * (panel_simple.extra_rates), and the panel driver adds a mode per rate. They are then
 * picked in Peak refresh rate, whose 15 second trial brings back a rate that doesn't show.
 * A rate can also be previewed for 5 seconds without a restart: the andr36oid-hz-preview
 * root helper passes it to the kernel (preview_rate of the DSI host), and the kernel puts
 * the old rate back by itself after 5 seconds. Rates are kept in tenths of a Hz here.
 */
public class PanelOverclockFragment extends SettingsPreferenceFragment {

    private static final String TAG = "PanelOverclock";

    private static final String RATES_PROP = "persist.andr36oid.kt.extra_rates";
    /** what this start got, and the console's own default, from the helper */
    private static final String CURRENT_PROP = "sys.andr36oid.ktc.extra_rates";
    private static final String DEFAULT_PROP = "sys.andr36oid.ktd.extra_rates";
    private static final String PAUSED_PROP = "persist.andr36oid.kt.paused";
    private static final String REQUEST_PROP = "sys.andr36oid.kt.request";
    private static final String DONE_PROP = "sys.andr36oid.kt.done";
    private static final String FAILED_PROP = "sys.andr36oid.kt.failed";
    private static final String SERVICE = "andr36oid-ktweaks";
    /** the 5 second preview: rate and request in, "<request> <answer>" out */
    private static final String PREVIEW_RATE_PROP = "sys.andr36oid.hzp.rate";
    private static final String PREVIEW_REQUEST_PROP = "sys.andr36oid.hzp.request";
    private static final String PREVIEW_RESULT_PROP = "sys.andr36oid.hzp.result";
    private static final String PREVIEW_SERVICE = "andr36oid-hz-preview";
    /** the kernel's preview time, and how long the helper gets to answer */
    private static final int PREVIEW_S = 5;
    private static final long PREVIEW_WAIT_MS = 3000;
    /** Settings.Global, 1 once the damage warning was accepted */
    private static final String WARNED = "andr36oid_panel_overclock_warned";

    private static final ComponentName SMOOTHNESS_TEST = new ComponentName(
            "org.andr36oid.smoothnesstest", "org.andr36oid.smoothnesstest.SmoothnessTestActivity");

    /** the list to pick from, minus the rates the screen offers already */
    private static final int[] PRESETS = {500, 550, 650, 700, 750, 800, 850, 900, 1000, 1100,
            1200};
    /** the kernel's limits: 30 to 150 Hz, 16 rates */
    private static final int MIN = 300;
    private static final int MAX = 1500;
    private static final int MAX_RATES = 16;
    /** a preset this close to an offered rate isn't offered */
    private static final float PRESET_NEAR_HZ = 0.5f;
    /** a saved rate this close to a mode is that mode */
    private static final float SAME_HZ = 0.06f;
    private static final long POLL_MS = 200;
    private static final long WAIT_MS = 5000;
    private static final int WARN_DELAY_S = 3;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** apart from mHandler, whose callbacks apply() clears */
    private final Handler mPreviewHandler = new Handler(Looper.getMainLooper());
    private Preference mScreen;
    private PreferenceCategory mRates;
    private Preference mAdd;
    private Preference mRestart;
    private Preference mRemoveAll;
    private Preference mTest;
    private int mLastCustom = 750;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.andr36oid_panel_overclock;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mScreen = findPreference("screen");
        mRates = findPreference("rates");
        mAdd = findPreference("add");
        mRestart = findPreference("restart");
        mRemoveAll = findPreference("remove_all");
        mTest = findPreference("test");

        mAdd.setOnPreferenceClickListener(p -> {
            warnFirst(this::showAdd);
            return true;
        });
        mRestart.setOnPreferenceClickListener(p -> {
            confirmRestart();
            return true;
        });
        mRemoveAll.setOnPreferenceClickListener(p -> {
            save(new TreeSet<>());
            return true;
        });
        mTest.setOnPreferenceClickListener(p -> {
            try {
                startActivity(new Intent().setComponent(SMOOTHNESS_TEST));
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "No smoothness test", e);
            }
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        mPreviewHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------------------------- state

    /** "70,77.5" -> tenths of a Hz; empty and "none" are none */
    private static TreeSet<Integer> parse(String list) {
        final TreeSet<Integer> out = new TreeSet<>();
        for (String s : list.split(",")) {
            try {
                final int t = Math.round(Float.parseFloat(s.trim()) * 10);
                if (t >= MIN && t <= MAX) out.add(t);
            } catch (NumberFormatException e) {
                // "none", "" or junk
            }
        }
        return out;
    }

    /** tenths -> "77.5" or "78", as the kernel takes it */
    private static String value(int tenths) {
        return tenths % 10 == 0 ? Integer.toString(tenths / 10)
                : String.format(Locale.US, "%d.%d", tenths / 10, tenths % 10);
    }

    /** a rate for people: up to two decimals in their own way of writing them */
    private static String label(float hz) {
        return String.format(Locale.getDefault(), "%.2f", hz).replaceAll("[.,]?0+$", "");
    }

    private TreeSet<Integer> saved() {
        final String v = SystemProperties.get(RATES_PROP, "");
        return parse(v.isEmpty() ? SystemProperties.get(DEFAULT_PROP, "") : v);
    }

    /** the rates this start got on the command line */
    private TreeSet<Integer> started() {
        return parse(SystemProperties.get(CURRENT_PROP, ""));
    }

    private boolean paused() {
        final String v = SystemProperties.get(PAUSED_PROP, "");
        return "1".equals(v) || "crash".equals(v);
    }

    /** the screen's modes at its current size; the first one is the panel's own */
    private List<Display.Mode> modes() {
        final List<Display.Mode> out = new ArrayList<>();
        final Display d = getContext().getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
        if (d == null) return out;
        final Display.Mode cur = d.getMode();
        for (Display.Mode m : d.getSupportedModes()) {
            if (m.getPhysicalWidth() == cur.getPhysicalWidth()
                    && m.getPhysicalHeight() == cur.getPhysicalHeight()) {
                out.add(m);
            }
        }
        // the panel's own mode is the kernel's first, so it gets the lowest id
        out.sort((a, b) -> Integer.compare(a.getModeId(), b.getModeId()));
        return out;
    }

    private static boolean offered(List<Display.Mode> modes, float hz, float near) {
        for (Display.Mode m : modes) {
            if (Math.abs(m.getRefreshRate() - hz) < near) return true;
        }
        return false;
    }

    private void refresh() {
        final Context c = getPrefContext();
        final List<Display.Mode> modes = modes();
        final TreeSet<Integer> saved = saved();
        final TreeSet<Integer> started = started();
        final boolean paused = paused();

        if (!modes.isEmpty()) {
            final TreeSet<Float> rates = new TreeSet<>();
            for (Display.Mode m : modes) rates.add(m.getRefreshRate());
            final List<String> names = new ArrayList<>();
            for (float r : rates) names.add(label(r));
            CharSequence summary = getString(R.string.andr36oid_panel_overclock_screen_summary,
                    label(modes.get(0).getRefreshRate()), String.join(", ", names));
            if (paused) {
                summary = summary + "\n" + getString(R.string.andr36oid_panel_overclock_paused);
            }
            mScreen.setSummary(summary);
        }

        mRates.removeAll();
        if (saved.isEmpty()) {
            final Preference none = new Preference(c);
            none.setTitle(R.string.andr36oid_panel_overclock_rates_none);
            mRates.addPreference(none);
        }
        for (int t : saved) {
            final Preference row = new Preference(c);
            row.setTitle(getString(R.string.andr36oid_panel_overclock_rate, label(t / 10f)));
            if (!started.contains(t)) {
                row.setSummary(R.string.andr36oid_panel_overclock_rate_pending);
            } else if (offered(modes, t / 10f, SAME_HZ)) {
                row.setSummary(R.string.andr36oid_panel_overclock_rate_offered);
            } else {
                row.setSummary(R.string.andr36oid_panel_overclock_rate_left_out);
            }
            row.setOnPreferenceClickListener(p -> {
                showRate(t);
                return true;
            });
            mRates.addPreference(row);
        }

        final boolean changed = !saved.equals(started);
        mRestart.setEnabled(changed && !paused);
        mRestart.setTitle(saved.containsAll(started) ? R.string.andr36oid_panel_overclock_restart_add
                : R.string.andr36oid_panel_overclock_restart_remove);
        mRestart.setSummary(changed ? null
                : getString(R.string.andr36oid_panel_overclock_restart_nothing));
        mRemoveAll.setEnabled(!saved.isEmpty());
    }

    /** save the rates and have the helper write them for the next start */
    private void save(TreeSet<Integer> rates) {
        final List<String> values = new ArrayList<>();
        for (int t : rates) values.add(value(t));
        SystemProperties.set(RATES_PROP, String.join(",", values));
        apply(answered -> refresh());
    }

    /** ask the root helper to write everything, then done(whether it answered in time) */
    private void apply(Consumer<Boolean> done) {
        final String request = Long.toString(SystemClock.elapsedRealtime());
        final long until = SystemClock.uptimeMillis() + WAIT_MS;
        SystemProperties.set(REQUEST_PROP, request);
        SystemProperties.set("ctl.start", SERVICE);
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final boolean answered = request.equals(SystemProperties.get(DONE_PROP, ""));
                if (answered || SystemClock.uptimeMillis() > until) {
                    if (getActivity() != null) done.accept(answered);
                    return;
                }
                mHandler.postDelayed(this, POLL_MS);
            }
        }, POLL_MS);
    }

    // ----------------------------------------------------------------------------- dialogs

    /** the damage warning unless it was accepted before, then next */
    private void warnFirst(Runnable next) {
        if (Settings.Global.getInt(getContentResolver(), WARNED, 0) == 1) {
            next.run();
        } else {
            showWarning(next);
        }
    }

    /** the damage warning, once: accept only after ticking the box */
    private void showWarning(Runnable next) {
        final Context c = getContext();
        final CheckBox box = new CheckBox(c);
        box.setText(R.string.andr36oid_panel_overclock_warning_check);
        final LinearLayout frame = new LinearLayout(c);
        final int pad = (int) (20 * getResources().getDisplayMetrics().density);
        frame.setPadding(pad, pad / 2, pad, 0);
        frame.addView(box);
        final AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle(R.string.andr36oid_panel_overclock_warning_title)
                .setMessage(R.string.andr36oid_panel_overclock_warning)
                .setView(frame)
                .setPositiveButton(R.string.andr36oid_panel_overclock_warning_accept, (d, w) -> {
                    Settings.Global.putInt(getContentResolver(), WARNED, 1);
                    next.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        final Button accept = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        accept.setEnabled(false);
        box.setEnabled(false);
        // a few seconds to read it before the box can be ticked
        mHandler.postDelayed(() -> box.setEnabled(true), WARN_DELAY_S * 1000L);
        box.setOnCheckedChangeListener((v, on) -> accept.setEnabled(on));
    }

    private void showAdd() {
        final List<Display.Mode> modes = modes();
        final TreeSet<Integer> saved = saved();
        final List<Integer> choices = new ArrayList<>();
        for (int t : PRESETS) {
            if (saved.contains(t) || !offered(modes, t / 10f, PRESET_NEAR_HZ)) choices.add(t);
        }
        final CharSequence[] names = new CharSequence[choices.size()];
        final boolean[] checked = new boolean[choices.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = getString(R.string.andr36oid_panel_overclock_rate,
                    label(choices.get(i) / 10f));
            checked[i] = saved.contains(choices.get(i));
        }
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.andr36oid_panel_overclock_add)
                .setMultiChoiceItems(names, checked, (d, which, on) -> checked[which] = on)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    final TreeSet<Integer> next = new TreeSet<>(saved);
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) next.add(choices.get(i));
                        else next.remove(choices.get(i));
                    }
                    saveChecked(next);
                })
                .setNeutralButton(R.string.andr36oid_panel_overclock_custom, (d, w) -> showCustom())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** a rate of their own: whole Hz and tenths, set with the d-pad */
    private void showCustom() {
        final Context c = getContext();
        final NumberPicker whole = new NumberPicker(c);
        whole.setMinValue(MIN / 10);
        whole.setMaxValue(MAX / 10);
        whole.setValue(mLastCustom / 10);
        whole.setWrapSelectorWheel(false);
        final NumberPicker tenth = new NumberPicker(c);
        tenth.setMinValue(0);
        tenth.setMaxValue(9);
        tenth.setValue(mLastCustom % 10);
        // the pickers move with up and down, not through a keyboard
        whole.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        tenth.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(whole);
        final TextView dot = new TextView(c);
        dot.setText(String.valueOf(new DecimalFormatSymbols().getDecimalSeparator()));
        row.addView(dot);
        row.addView(tenth);
        final TextView unit = new TextView(c);
        unit.setText(" Hz");
        row.addView(unit);

        new AlertDialog.Builder(c)
                .setTitle(R.string.andr36oid_panel_overclock_custom_title)
                .setMessage(R.string.andr36oid_panel_overclock_custom_message)
                .setView(row)
                .setPositiveButton(R.string.andr36oid_panel_overclock_custom_add, (d, w) -> {
                    final int t = Math.min(MAX, whole.getValue() * 10 + tenth.getValue());
                    mLastCustom = t;
                    final TreeSet<Integer> next = saved();
                    next.add(t);
                    saveChecked(next);
                })
                .setNeutralButton(R.string.andr36oid_panel_overclock_preview, (d, w) -> {
                    final int t = Math.min(MAX, whole.getValue() * 10 + tenth.getValue());
                    mLastCustom = t;
                    warnFirst(() -> preview(t));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        whole.requestFocus();
    }

    /** save, unless that's more rates than the kernel takes */
    private void saveChecked(TreeSet<Integer> rates) {
        if (rates.size() > MAX_RATES) {
            Toast.makeText(getContext(), R.string.andr36oid_panel_overclock_full,
                    Toast.LENGTH_LONG).show();
            return;
        }
        save(rates);
    }

    /** a saved rate: preview it or remove it */
    private void showRate(int tenths) {
        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.andr36oid_panel_overclock_rate,
                        label(tenths / 10f)))
                .setPositiveButton(R.string.andr36oid_panel_overclock_preview,
                        (d, w) -> warnFirst(() -> preview(tenths)))
                .setNeutralButton(R.string.andr36oid_panel_overclock_remove, (d, w) -> {
                    final TreeSet<Integer> next = saved();
                    next.remove(tenths);
                    save(next);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ----------------------------------------------------------------------------- preview

    /** have the root helper pass a rate (or 0, back now) to the kernel; done(its answer) */
    private void askPreview(String rate, Consumer<String> done) {
        final String request = Long.toString(SystemClock.elapsedRealtime());
        final long until = SystemClock.uptimeMillis() + PREVIEW_WAIT_MS;
        SystemProperties.set(PREVIEW_RATE_PROP, rate);
        SystemProperties.set(PREVIEW_REQUEST_PROP, request);
        SystemProperties.set("ctl.start", PREVIEW_SERVICE);
        mPreviewHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final String result = SystemProperties.get(PREVIEW_RESULT_PROP, "");
                final boolean answered = result.startsWith(request + " ");
                if (answered || SystemClock.uptimeMillis() > until) {
                    if (getActivity() != null) {
                        done.accept(answered ? result.substring(request.length() + 1)
                                : "failed");
                    }
                    return;
                }
                mPreviewHandler.postDelayed(this, POLL_MS);
            }
        }, POLL_MS);
    }

    /** show a rate for 5 seconds; the kernel puts the old one back by itself */
    private void preview(int tenths) {
        final String name = label(tenths / 10f);
        askPreview(value(tenths), answer -> {
            final int error;
            switch (answer) {
                case "ok":
                    showCountdown(tenths);
                    return;
                case "busy":
                    error = R.string.andr36oid_panel_overclock_preview_busy;
                    break;
                case "range":
                    error = R.string.andr36oid_panel_overclock_preview_range;
                    break;
                case "off":
                    error = R.string.andr36oid_panel_overclock_preview_off;
                    break;
                case "missing":
                    error = R.string.andr36oid_panel_overclock_preview_missing;
                    break;
                default:
                    error = R.string.andr36oid_panel_overclock_preview_failed;
                    break;
            }
            Toast.makeText(getContext(), getString(error, name), Toast.LENGTH_LONG).show();
        });
    }

    /** "Previewing 78 Hz, back in 5 s", counting down; then the question */
    private void showCountdown(int tenths) {
        final String name = label(tenths / 10f);
        final long end = SystemClock.uptimeMillis() + PREVIEW_S * 1000L;
        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setMessage(getString(R.string.andr36oid_panel_overclock_previewing, name,
                        PREVIEW_S))
                .setCancelable(false)
                .setNegativeButton(R.string.andr36oid_panel_overclock_preview_end, null)
                .create();
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (getActivity() == null) return;
                final long left = end - SystemClock.uptimeMillis();
                if (left <= 0) {
                    dialog.dismiss();
                    askLookedRight(tenths);
                    return;
                }
                dialog.setMessage(getString(R.string.andr36oid_panel_overclock_previewing, name,
                        (int) ((left + 999) / 1000)));
                mPreviewHandler.postDelayed(this, Math.min(left, (left - 1) % 1000 + 1));
            }
        };
        dialog.show();
        // back now: the kernel ends the preview at once, the dialog stays until it answered
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(v -> {
            v.setEnabled(false);
            mPreviewHandler.removeCallbacks(tick);
            askPreview("0", answer -> {
                dialog.dismiss();
                askLookedRight(tenths);
            });
        });
        mPreviewHandler.postDelayed(tick, 1000);
    }

    /** purely for them: yes offers to add the rate when it isn't saved yet */
    private void askLookedRight(int tenths) {
        final boolean saved = saved().contains(tenths);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.andr36oid_panel_overclock_preview_question)
                .setPositiveButton(R.string.andr36oid_panel_overclock_preview_yes, (d, w) -> {
                    if (!saved) offerAdd(tenths);
                })
                .setNegativeButton(R.string.andr36oid_panel_overclock_preview_no, null)
                .show();
    }

    private void offerAdd(int tenths) {
        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.andr36oid_panel_overclock_preview_offer,
                        label(tenths / 10f)))
                .setMessage(R.string.andr36oid_panel_overclock_preview_offer_message)
                .setPositiveButton(R.string.andr36oid_panel_overclock_custom_add, (d, w) -> {
                    final TreeSet<Integer> next = saved();
                    next.add(tenths);
                    saveChecked(next);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmRestart() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.andr36oid_panel_overclock_restart_question)
                .setMessage(getString(R.string.andr36oid_panel_overclock_restart_message,
                        getString(R.string.andr36oid_panel_overclock_warning_line)))
                .setPositiveButton(R.string.andr36oid_panel_overclock_restart, (d, w) -> restart())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** write the rates once more, and restart only when they reached BOOT */
    private void restart() {
        apply(answered -> {
            final List<String> failed = Arrays.asList(
                    SystemProperties.get(FAILED_PROP, "").split("\\s+"));
            if (!answered || failed.contains("boot")) {
                Toast.makeText(getContext(), R.string.andr36oid_panel_overclock_save_failed,
                        Toast.LENGTH_LONG).show();
                refresh();
                return;
            }
            getContext().getSystemService(PowerManager.class).reboot(null);
        });
    }
}
