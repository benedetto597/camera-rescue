# Orchestrator Handoff — Camera Rescue

> **Fuente de verdad del estado actual del proyecto.** Lee este archivo antes de planificar o ejecutar la siguiente fase.

## 1. Estado Actual Verificado

- **Versión liberada:** `v1.3.0` (`versionCode = 4`, `minSdk = 30`, `targetSdk = 34`, `compileSdk = 34`)
- **Rama principal:** `main` (sincronizada con `origin/main`)
- **Último commit de código/documentación previo al handoff:** `328fd62` (`docs: document v1.3.0 dynamic selector, UI builder, shortcuts, and quick tile`)
- **Tag de lanzamiento:** `v1.3.0`
- **Artefacto APK local verificado:** `dist/camera-rescue-v1.3.0.apk` (`58,572` bytes, firmado v1+v2+v3 con `apksigner`)
- **Estado de pruebas unitarias (`scripts/test-local.ps1`):** **28/28 PASS** en 4 suites puras de Java:
  - `CameraConfigTest`: `7/7` PASS
  - `SamsungModelDatabaseTest`: `8/8` PASS
  - `SensorSelectionStateTest`: `5/5` PASS
  - `DynamicUiBuilderTest`: `8/8` PASS
- **Estado de compilación (`scripts/build-local.ps1`):** Exit code `0` (`aapt2` + `javac -source 17 -target 17` + `d8` + `zipalign` + `apksigner`)

---

## 2. Resumen de Entregables Completados en `v1.3.0` (Fase 1)

| Commit | Alcance | Archivos Clave |
| :--- | :--- | :--- |
| `82c1598` | Modelos puros, estado de selección y constantes `v1.3.0` | `CameraConfig.java`, `RescueActionSpec.java`, `SensorSelectionState.java`, `CameraConfigTest.java`, `SensorSelectionStateTest.java`, `scripts/test-local.ps1` |
| `733b46c` | Motor `DynamicUiBuilder` + corrección de polaridad `facing` y deduplicación en `SensorScanner` | `DynamicUiBuilder.java`, `DynamicUiBuilderTest.java`, `SensorScanner.java`, `scripts/test-local.ps1` |
| `3370b2a` | Persistencia, Launcher App Shortcuts, Quick Settings Tile, iconos vectoriales y `AndroidManifest.xml` | `RescuePreferences.java`, `RescueShortcutManager.java`, `CameraRescueTileService.java`, `ic_qs_camera_rescue.xml`, `ic_shortcut_{pro,qr,video,front}.xml`, `AndroidManifest.xml` |
| `8eb91bc` | Checklist interactivo (`CheckBox`), reconstrucción dinámica de tarjetas y visor frontal, despacho `singleTask` y sincronización Gradle/CI | `MainActivity.java`, `FrontCameraController.java`, `app/build.gradle.kts`, `scripts/build-local.ps1`, `.github/workflows/release-apk.yml` |
| `328fd62` | Documentación de arquitectura/roadmap/README, plan de `v1.3.0` y APK firmado | `README.md`, `docs/ROADMAP.md`, `docs/architecture.md`, `docs/superpowers/plans/2026-11-25-v1.3.0-dynamic-selector-ui-builder.md`, `dist/camera-rescue-v1.3.0.apk` |

---

## 3. Decisiones Técnicas y Reglas de Arquitectura Vigentes

1. **Compatibilidad Android 14+ (API 34) en `CameraRescueTileService`:**
   - En `targetSdk = 34`, `TileService.startActivityAndCollapse(Intent)` lanza `UnsupportedOperationException` en Android 14+.
   - `CameraRescueTileService.onClick()` bifurca por `Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE` usando `startActivityAndCollapse(PendingIntent)` (`FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`), manteniendo `startActivityAndCollapse(Intent)` como fallback en API 30–33.
2. **Normalización de polaridad `facing` y deduplicación en `SensorScanner`:**
   - `DiscoveredSensor.facing` usa `0 = BACK, 1 = FRONT` (mientras Android `CameraCharacteristics.LENS_FACING_FRONT == 0`). `SensorScanner.testCameraSensor()` normaliza explícitamente `(facingVal == LENS_FACING_FRONT) ? 1 : 0`.
   - `SensorScanner.performScanInternal()` omite registrar IDs independientes del perfil si ya existen en `publicCameraIds` (evita duplicados en modelos como Galaxy S9 / Note 9 donde el principal es `"0"`).
3. **Consumo seguro de Intents en `singleTask` (`MainActivity`):**
   - `handleLaunchIntent(Intent)` se invoca en `onCreate` y `onNewIntent`, y restablece inmediatamente `setIntent(new Intent(Intent.ACTION_MAIN))` al despachar `ACTION_LAUNCH_*` para evitar re-ejecuciones al volver desde Recents.
4. **Cero dependencias externas & 100% offline:**
   - Sin librerías de terceros; pruebas unitarias en Java puro sin JUnit; sin `android.permission.INTERNET`.
   - El paquete `com.s23plus.camera` y `RearCaptureService` permanecen sin cambios estructurales en `v1.3.0` (su reestructuración para tienda corresponde a Fase 4).

---

## 4. Hitos Pendientes del Roadmap

1. **SIGUIENTE — Fase 2 (`v1.4.0`): Mapeador Heurístico & Asistente One UI**
   - Ampliar la base de datos heurística / `ShootingModeMap` cubriendo series Galaxy S9–S25, Note, Z Fold/Flip y Serie A (incluyendo variantes de nombres de modos Pro / Pro video / QR según versión de One UI y particularidades de hardware).
   - Implementar asistente interactivo guiado para configurar **"Ajustes para conservar" (*Settings to keep*)** y **Tecla lateral (doble toque)** en One UI, con acceso directo a ajustes relevantes o guía paso a paso verificable.
   - Implementar detector/recuperación rápida ante bloqueos o estados atascados de cámara (por ejemplo, recuperación rápida hacia Modo Pro cuando la app nativa queda atrapada en Foto / Ultra-Wide, o limpieza determinista de sesión local).
2. **Fase 3 (`v2.0.0`): Camera Rescue Universal**
   - Visor nativo `Camera2` completo con zoom digital seguro sobre el sensor sano, controles de captura (EV, flash, temporizador, cuadrícula), grabación de video MP4 transaccional en `MediaStore` y exportación/importación de perfiles `.json`.
   - Soporte de apertura variable mecánica ($f/1.5 \leftrightarrow f/2.4$) en dispositivos compatibles (S9/S10).
3. **Fase 4 (`Lanzamiento Producción`): Play Store Readiness**
   - Cumplimiento integral de políticas de tienda (branding/paquete libre de marcas registradas, arquitectura limpia respecto a `AccessibilityService` o cumplimiento documentado, `targetSdk` requerido, política de privacidad, firma de release/AAB segura sin exponer keystores en Git, assets y verificación final).

---

## 5. Siguiente Paso Exacto

1. Leer este handoff (`docs/ORCHESTRATOR_HANDOFF.md`), `docs/ROADMAP.md` y el estado de `SamsungModelDatabase.java` + `CameraConfig.java` + `MainActivity.java`.
2. Usar `superpowers:writing-plans` para redactar el plan técnico detallado de **Fase 2 (`v1.4.0`)** en `docs/superpowers/plans/`.
3. Ejecutar la Fase 2 (`v1.4.0`) mediante subagentes frescos (implementador + revisor) con TDD estricto en la lógica pura (`ShootingModeMap` / asistente / recuperación), verificación real de `scripts/test-local.ps1` y `scripts/build-local.ps1`, documentación, tag `v1.4.0` y actualización de este handoff antes de cualquier compactación de contexto.
