package com.s23plus.camera;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestor de accesos directos dinámicos y anclables en el lanzador (App Shortcuts)
 * generados a partir de las acciones activas del selector de sensores.
 */
public final class RescueShortcutManager {

    private RescueShortcutManager() {
        // Clase de utilidad no instanciable
    }

    /**
     * Publica hasta {@link CameraConfig#MAX_DYNAMIC_SHORTCUTS} accesos directos dinámicos
     * en el sistema. Si la lista está vacía, limpia los accesos dinámicos previos.
     *
     * @return cantidad de accesos directos dinámicos publicados.
     */
    public static int updateDynamicShortcuts(Context context, List<RescueActionSpec> shortcutSpecs) {
        if (context == null) {
            return 0;
        }
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager == null) {
            return 0;
        }
        if (shortcutSpecs == null || shortcutSpecs.isEmpty()) {
            try {
                shortcutManager.removeAllDynamicShortcuts();
            } catch (RuntimeException ignored) {
                // Algunos launchers restringen modificaciones en segundo plano
            }
            return 0;
        }

        int limit = Math.min(
                CameraConfig.MAX_DYNAMIC_SHORTCUTS,
                Math.max(1, shortcutManager.getMaxShortcutCountPerActivity())
        );

        List<ShortcutInfo> infos = new ArrayList<>(limit);
        for (RescueActionSpec spec : shortcutSpecs) {
            if (spec == null) {
                continue;
            }
            if (infos.size() >= limit) {
                break;
            }
            infos.add(buildShortcutInfo(context, spec, infos.size()));
        }

        if (infos.isEmpty()) {
            try {
                shortcutManager.removeAllDynamicShortcuts();
            } catch (RuntimeException ignored) {
                // Ignorar error de sistema
            }
            return 0;
        }

        try {
            shortcutManager.setDynamicShortcuts(infos);
            return infos.size();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * Solicita al lanzador anclar un acceso directo individual en la pantalla de inicio.
     */
    public static boolean requestPinShortcut(Context context, RescueActionSpec spec) {
        if (context == null || spec == null) {
            return false;
        }
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported()) {
            return false;
        }
        try {
            ShortcutInfo info = buildShortcutInfo(context, spec, 0);
            return shortcutManager.requestPinShortcut(info, null);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static ShortcutInfo buildShortcutInfo(Context context, RescueActionSpec spec, int rank) {
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setAction(spec.intentAction);
        if (spec.sensorId != null && !spec.sensorId.trim().isEmpty()) {
            launchIntent.putExtra(CameraConfig.EXTRA_SHORTCUT_SENSOR_ID, spec.sensorId);
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        String shortLabel = (spec.shortcutShortLabel != null && !spec.shortcutShortLabel.trim().isEmpty())
                ? spec.shortcutShortLabel
                : spec.title;
        String longLabel = (spec.shortcutLongLabel != null && !spec.shortcutLongLabel.trim().isEmpty())
                ? spec.shortcutLongLabel
                : spec.title;

        int iconRes = resolveIconRes(spec.actionType);

        return new ShortcutInfo.Builder(context, spec.id)
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(Icon.createWithResource(context, iconRes))
                .setIntent(launchIntent)
                .setRank(rank)
                .build();
    }

    private static int resolveIconRes(RescueActionSpec.ActionType actionType) {
        switch (actionType) {
            case REAR_PRO_PHOTO:
                return R.drawable.ic_shortcut_pro;
            case REAR_QR_DIRECT:
                return R.drawable.ic_shortcut_qr;
            case REAR_PRO_VIDEO:
                return R.drawable.ic_shortcut_video;
            case FRONT_CAMERA:
            default:
                return R.drawable.ic_shortcut_front;
        }
    }
}
