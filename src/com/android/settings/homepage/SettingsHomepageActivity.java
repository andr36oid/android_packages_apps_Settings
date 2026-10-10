/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.homepage;

import android.animation.LayoutTransition;
import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toolbar;

import androidx.annotation.VisibleForTesting;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.accounts.AvatarViewMixin;
import com.android.settings.core.HideNonSystemOverlayMixin;
import com.android.settings.homepage.contextualcards.ContextualCardsFragment;
import com.android.settings.overlay.FeatureFactory;

public class SettingsHomepageActivity extends FragmentActivity {

    // How long after opening the first entry stays focused at the top. The injected entries
    // and the cards arrive later and would push a focused entry down the page.
    private static final long PIN_TOP_MS = 2000;
    private long mPinTopUntil;
    private final ViewTreeObserver.OnGlobalLayoutListener mPinTop = this::pinTop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure device is provisioned in order to access Settings home
        // TODO(b/331254029): This should later be replaced in favor of an allowlist
        boolean unprovisioned = android.provider.Settings.Global.getInt(getContentResolver(),
                android.provider.Settings.Global.DEVICE_PROVISIONED, 0) == 0;
        if (unprovisioned) {
            Log.e("SettingsHomepageActivity", "Device is not provisioned, exiting Settings");
            finish();
            return;
        }

        setContentView(R.layout.settings_homepage_container);
        final View root = findViewById(R.id.settings_homepage_container);
        root.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        setHomepageContainerPaddingTop();

        final Toolbar toolbar = findViewById(R.id.search_action_bar);
        FeatureFactory.getFactory(this).getSearchFeatureProvider()
                .initSearchToolbar(this /* activity */, toolbar, SettingsEnums.SETTINGS_HOMEPAGE);

        final ImageView avatarView = findViewById(R.id.account_avatar);
        getLifecycle().addObserver(new AvatarViewMixin(this, avatarView));
        getLifecycle().addObserver(new HideNonSystemOverlayMixin(this));

        if (!getSystemService(ActivityManager.class).isLowRamDevice()) {
            // Only allow contextual feature on high ram devices.
            showFragment(new ContextualCardsFragment(), R.id.contextual_cards_content);
        }
        showFragment(new TopLevelSettings(), R.id.main_content);
        ((FrameLayout) findViewById(R.id.main_content))
                .getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);

        focusSettingsList();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Launching Settings while it is still running lands here (singleTask).
        focusSettingsList();
    }

    /**
     * The device has no touchscreen, so start with the first entry (Network & internet)
     * focused, at the top of the page. Otherwise the d-pad just scrolls the page. Until the
     * user presses a key, every layout in the first seconds puts it back, as entries that
     * arrive later would push it down the page. requestFocusFromTouch() also leaves touch
     * mode, which a mouse click enters.
     */
    private void focusSettingsList() {
        final View scrollContainer = findViewById(R.id.main_content_scrollable_container);
        if (scrollContainer == null) {
            return;
        }
        mPinTopUntil = SystemClock.uptimeMillis() + PIN_TOP_MS;
        final ViewTreeObserver observer = scrollContainer.getViewTreeObserver();
        observer.removeOnGlobalLayoutListener(mPinTop);
        observer.addOnGlobalLayoutListener(mPinTop);
        scrollContainer.post(this::pinTop);
    }

    private void pinTop() {
        final NestedScrollView scrollContainer =
                findViewById(R.id.main_content_scrollable_container);
        if (scrollContainer == null) {
            return;
        }
        if (SystemClock.uptimeMillis() > mPinTopUntil) {
            scrollContainer.getViewTreeObserver().removeOnGlobalLayoutListener(mPinTop);
            return;
        }
        final Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_content);
        if (!(fragment instanceof PreferenceFragmentCompat)) {
            return;
        }
        final RecyclerView list = ((PreferenceFragmentCompat) fragment).getListView();
        if (list == null || list.getLayoutManager() == null) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            final View entry = list.getLayoutManager().findViewByPosition(i);
            if (entry != null && entry.isFocusable()) {
                if (!entry.isFocused()) {
                    entry.requestFocusFromTouch();
                }
                if (scrollContainer.getScrollY() != 0) {
                    scrollContainer.scrollTo(0, 0);
                }
                return;
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The user moves on from here: stop holding the first entry. Not on a key up, which
        // may still belong to the press that opened Settings.
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mPinTopUntil = 0;
        }
        return super.dispatchKeyEvent(event);
    }

    private void showFragment(Fragment fragment, int id) {
        final FragmentManager fragmentManager = getSupportFragmentManager();
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        final Fragment showFragment = fragmentManager.findFragmentById(id);

        if (showFragment == null) {
            fragmentTransaction.add(id, fragment);
        } else {
            fragmentTransaction.show(showFragment);
        }
        fragmentTransaction.commit();
    }

    @VisibleForTesting
    void setHomepageContainerPaddingTop() {
        final View view = this.findViewById(R.id.homepage_container);

        final int searchBarHeight = getResources().getDimensionPixelSize(R.dimen.search_bar_height);
        final int searchBarMargin = getResources().getDimensionPixelSize(R.dimen.search_bar_margin);

        // The top padding is the height of action bar(48dp) + top/bottom margins(16dp)
        final int paddingTop = searchBarHeight + searchBarMargin * 2;
        view.setPadding(0 /* left */, paddingTop, 0 /* right */, 0 /* bottom */);
    }
}
