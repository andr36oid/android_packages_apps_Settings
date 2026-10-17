/*
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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SeekBarPreference;

import com.android.settings.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class CrtEffectFragmentTest {
    @Test
    public void sliders_bindWithSettingsTheme_showValues() {
        final Context context = new ContextThemeWrapper(
                RuntimeEnvironment.application, R.style.Theme_Settings);
        context.getTheme().applyStyle(R.style.PreferenceTheme, true);
        final PreferenceScreen screen = new PreferenceManager(context).inflateFromResource(
                context, R.xml.andr36oid_crt_effect, null);

        for (String key : new String[] {"strength", "curvature", "glow", "gpu_mask"}) {
            final SeekBarPreference slider = screen.findPreference(key);
            slider.setShowSeekBarValue(true);
            slider.setValue(50);
            final View row = LayoutInflater.from(context).inflate(
                    slider.getLayoutResource(), new FrameLayout(context), false);
            slider.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row));

            final TextView value = row.findViewById(androidx.preference.R.id.seekbar_value);
            assertThat(value.getVisibility()).isEqualTo(View.VISIBLE);
            assertThat(value.getText().toString()).isEqualTo("50");
            assertThat(row.findViewById(androidx.preference.R.id.seekbar)).isNotNull();
        }
    }
}
