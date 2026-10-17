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

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.settings.R;

import java.util.Arrays;

/**
 * Quick Settings tile switching the CRT effect on and off, with whatever the CRT effect page
 * has set up. Always in the tile editor, and put in the shade the first time the effect is
 * turned on (addOnce).
 */
public class CrtTileService extends TileService {

    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        CrtEffectFragment.setEnabled(this, !CrtEffectFragment.isEnabled());
        updateTile();
    }

    private void updateTile() {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(CrtEffectFragment.isEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    /** Adds the tile to the shade once, with a toast saying so. */
    static void addOnce(Context context) {
        final ContentResolver resolver = context.getContentResolver();
        final String spec = "custom("
                + new ComponentName(context, CrtTileService.class).flattenToShortString() + ")";
        String tiles = Settings.Secure.getString(resolver, Settings.Secure.QS_TILES);
        if (TextUtils.isEmpty(tiles)) {
            // Unset means the default tiles, which SystemUI expands "default" to.
            tiles = "default";
        }
        if (!Arrays.asList(tiles.split(",")).contains(spec)) {
            Settings.Secure.putString(resolver, Settings.Secure.QS_TILES, tiles + "," + spec);
            Toast.makeText(context, R.string.andr36oid_crt_tile_added, Toast.LENGTH_LONG).show();
        }
        TileService.requestListeningState(context,
                new ComponentName(context, CrtTileService.class));
    }
}
