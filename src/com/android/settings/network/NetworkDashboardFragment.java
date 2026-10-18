/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.settings.network;

import static com.android.settings.network.MobilePlanPreferenceController.MANAGE_MOBILE_PLAN_DIALOG_ID;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.connecteddevice.AvailableMediaDeviceGroupController;
import com.android.settings.connecteddevice.ConnectedDeviceDashboardFragment;
import com.android.settings.connecteddevice.ConnectedDeviceGroupController;
import com.android.settings.connecteddevice.DiscoverableFooterPreferenceController;
import com.android.settings.connecteddevice.PreviouslyConnectedDevicePreferenceController;
import com.android.settings.connecteddevice.SavedTwsDeviceGroupController;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.password.PasswordUtils;
import com.android.settings.network.MobilePlanPreferenceController.MobilePlanPreferenceHost;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.wifi.WifiMasterSwitchPreferenceController;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.drawer.CategoryKey;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SearchIndexable
public class NetworkDashboardFragment extends DashboardFragment implements
        MobilePlanPreferenceHost {

    private static final String TAG = "NetworkDashboardFrag";

    /** Preferences from this order on (Data Saver, VPN, Private DNS, tethering) are under Advanced. */
    private static final int ADVANCED_ORDER = 200;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_NETWORK_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.network_and_internet;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        use(MultiNetworkHeaderController.class).init(getSettingsLifecycle());
        use(AirplaneModePreferenceController.class).setFragment(this);
        getSettingsLifecycle().addObserver(use(AllInOneTetherPreferenceController.class));
        initConnectedDeviceControllers();
    }

    private void initConnectedDeviceControllers() {
        // andr36oid: the paired devices part of the old Connected devices page
        use(AvailableMediaDeviceGroupController.class).init(this);
        use(ConnectedDeviceGroupController.class).init(this);
        use(SavedTwsDeviceGroupController.class).init(this);
        use(PreviouslyConnectedDevicePreferenceController.class).init(this);
        final String callingAppPackageName = PasswordUtils.getCallingAppPackageName(
                getActivity().getActivityToken());
        final String action = getIntent() != null ? getIntent().getAction() : "";
        use(DiscoverableFooterPreferenceController.class).setAlwaysDiscoverable(
                ConnectedDeviceDashboardFragment.isAlwaysDiscoverable(
                        callingAppPackageName, action));
    }

    @Override
    public void onCategoriesChanged(Set<String> categories) {
        // andr36oid: tiles for the old Connected devices category are shown here as well
        if (categories != null && categories.contains(CategoryKey.CATEGORY_CONNECT)) {
            super.onCategoriesChanged(null);
            return;
        }
        super.onCategoriesChanged(categories);
    }

    @Override
    protected void onDataSetChanged() {
        super.onDataSetChanged();
        updateAdvancedCount();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // andr36oid: paired devices are added in the background after onDataSetChanged, which
        // would push the last rows before Advanced under it. Count again whenever the list is
        // rebuilt; the adapter only rebuilds once more if the count changed.
        final RecyclerView list = getListView();
        final RecyclerView.Adapter<?> adapter = list != null ? list.getAdapter() : null;
        if (adapter instanceof PreferenceGroupAdapter) {
            adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    if (updateAdvancedCount()) {
                        ((PreferenceGroupAdapter) adapter).onPreferenceHierarchyChange(null);
                    }
                }
            });
        }
    }

    /**
     * andr36oid: everything before the Advanced part (VPN, Private DNS, Hotspot & tethering) is
     * shown, however many paired devices and app tiles there are right now.
     */
    /** Returns whether the count changed. */
    private boolean updateAdvancedCount() {
        final PreferenceScreen screen = getPreferenceScreen();
        final Bundle arguments = getArguments();
        if (screen == null || (arguments != null
                && arguments.containsKey(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY))) {
            return false; // a search result is highlighted, everything stays expanded
        }
        if (screen.getInitialExpandedChildrenCount() == Integer.MAX_VALUE) {
            return false; // Advanced was opened (the expand button sets this), it stays open
        }
        final int count = countVisible(screen);
        if (count == screen.getInitialExpandedChildrenCount()) {
            return false;
        }
        screen.setInitialExpandedChildrenCount(count);
        return true;
    }

    private static int countVisible(PreferenceScreen screen) {
        int count = 0;
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            final Preference pref = screen.getPreference(i);
            if (!pref.isVisible() || pref.getOrder() >= ADVANCED_ORDER) {
                continue;
            }
            if (pref instanceof PreferenceGroup) {
                final PreferenceGroup group = (PreferenceGroup) pref;
                for (int j = 0; j < group.getPreferenceCount(); j++) {
                    if (group.getPreference(j).isVisible()) {
                        count++;
                    }
                }
            } else {
                count++;
            }
        }
        return count;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        use(AllInOneTetherPreferenceController.class).initEnabler(getSettingsLifecycle());
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_network_dashboard;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, getSettingsLifecycle(), mMetricsFeatureProvider,
                this /* fragment */, this /* mobilePlanHost */);
    }

    @Override
    protected boolean isParalleledControllers() {
        return true;
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context,
            Lifecycle lifecycle, MetricsFeatureProvider metricsFeatureProvider, Fragment fragment,
            MobilePlanPreferenceHost mobilePlanHost) {
        final MobilePlanPreferenceController mobilePlanPreferenceController =
                new MobilePlanPreferenceController(context, mobilePlanHost);
        final WifiMasterSwitchPreferenceController wifiPreferenceController =
                new WifiMasterSwitchPreferenceController(context, metricsFeatureProvider);

        final VpnPreferenceController vpnPreferenceController =
                new VpnPreferenceController(context);
        final PrivateDnsPreferenceController privateDnsPreferenceController =
                new PrivateDnsPreferenceController(context);

        if (lifecycle != null) {
            lifecycle.addObserver(mobilePlanPreferenceController);
            lifecycle.addObserver(wifiPreferenceController);
            lifecycle.addObserver(vpnPreferenceController);
            lifecycle.addObserver(privateDnsPreferenceController);
        }

        final List<AbstractPreferenceController> controllers = new ArrayList<>();

        controllers.add(new MobileNetworkSummaryController(context, lifecycle));
        controllers.add(new TetherPreferenceController(context, lifecycle));
        controllers.add(vpnPreferenceController);
        controllers.add(new ProxyPreferenceController(context));
        controllers.add(mobilePlanPreferenceController);
        controllers.add(wifiPreferenceController);
        controllers.add(privateDnsPreferenceController);
        return controllers;
    }

    @Override
    public void showMobilePlanMessageDialog() {
        showDialog(MANAGE_MOBILE_PLAN_DIALOG_ID);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        Log.d(TAG, "onCreateDialog: dialogId=" + dialogId);
        switch (dialogId) {
            case MANAGE_MOBILE_PLAN_DIALOG_ID:
                final MobilePlanPreferenceController controller =
                        use(MobilePlanPreferenceController.class);
                return new AlertDialog.Builder(getActivity())
                        .setMessage(controller.getMobilePlanDialogMessage())
                        .setCancelable(false)
                        .setPositiveButton(com.android.internal.R.string.ok,
                                (dialog, id) -> controller.setMobilePlanDialogMessage(null))
                        .create();
        }
        return super.onCreateDialog(dialogId);
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        if (MANAGE_MOBILE_PLAN_DIALOG_ID == dialogId) {
            return SettingsEnums.DIALOG_MANAGE_MOBILE_PLAN;
        }
        return 0;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.network_and_internet) {

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(Context
                        context) {
                    return buildPreferenceControllers(context, null /* lifecycle */,
                            null /* metricsFeatureProvider */, null /* fragment */,
                            null /* mobilePlanHost */);
                }
            };
}
