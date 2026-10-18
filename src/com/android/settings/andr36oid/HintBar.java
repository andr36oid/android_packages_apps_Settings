package com.android.settings.andr36oid;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.KeyEvent;

import com.android.settings.overlay.FeatureFactory;
import com.android.settings.search.SearchFeatureProvider;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settings.Utils;
import com.google.android.setupcompat.util.WizardManagerHelper;

/**
 * The console has no touch screen and the search bar on top could not be reached with the
 * d-pad, so Y opens search from any Settings page; the hint bar at the bottom says so.
 */
public final class HintBar {

    private HintBar() {
    }

    /**
     * Call from dispatchKeyEvent. Returns true when the event was Y and has been used.
     */
    public static boolean handleKey(Activity activity, KeyEvent event, int pageId) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_BUTTON_Y) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            openSearch(activity, pageId);
        }
        return true;
    }

    public static void openSearch(Activity activity, int pageId) {
        final Context context = activity.getApplicationContext();
        final SearchFeatureProvider provider =
                FeatureFactory.getFactory(context).getSearchFeatureProvider();
        if (!WizardManagerHelper.isDeviceProvisioned(activity)
                || !Utils.isPackageEnabled(activity,
                        provider.getSettingsIntelligencePkgName(activity))
                || WizardManagerHelper.isAnySetupWizard(activity.getIntent())) {
            return;
        }
        final Intent intent = provider.buildSearchIntent(context, pageId);
        if (activity.getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
            return;
        }
        FeatureFactory.getFactory(context).getSlicesFeatureProvider()
                .indexSliceDataAsync(context);
        final MetricsFeatureProvider metrics =
                FeatureFactory.getFactory(context).getMetricsFeatureProvider();
        metrics.action(context, SettingsEnums.ACTION_SEARCH_RESULTS);
        activity.startActivityForResult(intent, SearchFeatureProvider.REQUEST_CODE);
    }
}
