package com.s23plus.camera;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Mosaico de Ajustes Rápidos (Quick Settings Tile) para lanzar directamente
 * la acción de rescate configurada desde la barra de notificaciones.
 */
public final class CameraRescueTileService extends TileService {

    private static final String TILE_LABEL = "Rescue Cam";

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(Tile.STATE_ACTIVE);
        tile.setLabel(TILE_LABEL);
        tile.setSubtitle(RescuePreferences.loadQuickTileLabel(this));
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setAction(RescuePreferences.loadQuickTileAction(this));
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapseLegacy(launchIntent);
        }
    }

    @SuppressWarnings("deprecation")
    private void startActivityAndCollapseLegacy(Intent intent) {
        startActivityAndCollapse(intent);
    }
}
