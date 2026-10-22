package com.android.settings.system;

import android.app.settings.SettingsEnums;
import android.os.Bundle;
import android.os.SystemProperties;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class HotfixSettings extends SettingsPreferenceFragment {
    private static final String PAUSED = "persist.andr36oid.hotfix.paused";
    private static final String TRIES = "persist.andr36oid.hotfix.tries";
    private static final String STATUS = "/data/misc/andr36oid-hotfix/status";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.hotfix_settings);
        final SwitchPreference enabled = findPreference("hotfix_enabled");
        enabled.setOnPreferenceChangeListener((preference, value) -> {
            if ((Boolean) value) {
                SystemProperties.set(TRIES, "0");
                SystemProperties.set(PAUSED, "0");
            } else {
                SystemProperties.set(PAUSED, "1");
            }
            enabled.setSummary(R.string.hotfix_enabled_summary);
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        final SwitchPreference enabled = findPreference("hotfix_enabled");
        final String paused = SystemProperties.get(PAUSED);
        enabled.setChecked(!"1".equals(paused) && !"crash".equals(paused));
        enabled.setSummary("crash".equals(paused)
                ? R.string.hotfix_paused : R.string.hotfix_enabled_summary);
        final PreferenceCategory entries = findPreference("hotfix_entries");
        entries.removeAll();
        boolean available = true;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(STATUS), StandardCharsets.UTF_8))) {
            String line;
            int bytes = 0;
            while ((line = reader.readLine()) != null && (bytes += line.length()) < 65536) {
                final String[] fields = line.split("\t", -1);
                if (fields.length != 4 && fields.length != 5) continue;
                final Preference entry = new Preference(getPrefContext());
                entry.setTitle(fields[0]);
                final int result = "ran".equals(fields[1]) ? R.string.hotfix_ran
                        : "failed".equals(fields[1]) ? R.string.hotfix_failed
                        : R.string.hotfix_skipped;
                entry.setSummary(getString(R.string.hotfix_entry_summary,
                        fields[3], getString(result), getString(reason(fields[2])),
                        fields.length == 5 ? fields[4] : "BOOT"));
                entry.setSelectable(false);
                entries.addPreference(entry);
            }
        } catch (IOException e) {
            available = false;
        }
        if (entries.getPreferenceCount() == 0) {
            final Preference empty = new Preference(getPrefContext());
            empty.setTitle(available ? R.string.hotfix_none : R.string.hotfix_unavailable);
            empty.setSelectable(false);
            entries.addPreference(empty);
        }
    }

    private static int reason(String value) {
        switch (value) {
            case "ok": return R.string.hotfix_ok;
            case "build": return R.string.hotfix_build;
            case "disabled": return R.string.hotfix_disabled;
            case "paused": return R.string.hotfix_paused;
            case "safe": return R.string.hotfix_safe;
            case "invalid": return R.string.hotfix_invalid;
            case "timeout": return R.string.hotfix_timeout;
            case "exit": return R.string.hotfix_exit;
            case "limit": return R.string.hotfix_limit;
            case "counter": return R.string.hotfix_counter;
            case "storage": return R.string.hotfix_storage;
            case "duplicate": return R.string.hotfix_duplicate;
            default: return R.string.hotfix_unavailable;
        }
    }
}
