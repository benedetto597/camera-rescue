# Rescue Cam: Sensor Recovery (`io.github.benedetto597.rescuecam`)

> **Aviso de Independencia y Marcas Registradas:** *Rescue Cam es una herramienta de utilidad independiente. No está afiliada, autorizada, respaldada ni conectada con Samsung Electronics Co., Ltd. Los términos «Samsung», «Galaxy» y «One UI» se utilizan únicamente con fines descriptivos de compatibilidad técnica de hardware.*

<!-- en-summary -->
> **In English.** Offline Android camera utility that keeps Samsung Galaxy devices (S9 to S25) usable when a secondary rear camera sensor fails. Targets Android 16 (`targetSdk = 36`) with a **zero-accessibility architecture** (no `AccessibilityService`, no `INTERNET`, no broad storage permissions). Full documentation below is in Spanish.

- **Problem:** When a secondary sensor (for example the ultra-wide) fails, Samsung's camera HAL aborts the whole logical multi-camera session, so camera apps crash even though the main, telephoto, and front sensors still work.
- **Approach:** Launches native single-sensor shooting modes (Pro Photo, Pro Video, and direct 1x QR isolation) via a One UI-aware `ShootingModeMap` (Galaxy S9–S25, Note 9–20, Z Fold/Flip 1–6, A series) and provides a built-in universal `Camera2` viewfinder (`UniversalCameraController`) for operational front and public rear sensors with safe digital zoom, EV/flash/timer/grid controls, mechanical variable aperture ($f/1.5 \leftrightarrow f/2.4$), transactional MP4 recording (gracefully degrading to video-only if `RECORD_AUDIO` is denied), and offline `.json` profile export/import via Storage Access Framework.
- **Stack:** Java 17, Android Camera2 API + MediaRecorder (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 30`), zero third-party dependencies, R8/ProGuard shrinking, and automated CI for tagged APKs and manual Play Store AAB builds.
- **Privacy & Play Store Compliance:** [Static Privacy Policy (`docs/privacy-policy.html`)](docs/privacy-policy.html) · [Play Store Technical Readiness (`docs/play-store-readiness.md`)](docs/play-store-readiness.md) · [Security Policy (`SECURITY.md`)](SECURITY.md)
- **Build:** `scripts/build-local.ps1` produces `dist/camera-rescue-v2.1.0.apk`; install with `adb install -r`.

Aplicación de cámara minimalista, limpia, segura y **100% offline** diseñada como utilidad independiente de diagnóstico y recuperación de hardware para dispositivos **Samsung Galaxy** cuando un módulo físico secundario (como el Ultra-Wide `sensorId 2` / `IMX564`) presenta fallo eléctrico (EEPROM/I2C), manteniendo operativos el **lente trasero Principal 1x (`S5KGN3`, `ID 56`)**, el **Telefoto 3x (`S5K3K1`, `ID 52`)**, las **cámaras frontales (`S5K3LU`, `ID 1` e `ID 3`)** y cualquier sensor público operativo mediante visor `Camera2` nativo.

---

## 🔬 ¿Por qué fallan las apps de cámara convencionales y cómo lo resuelve Rescue Cam?

En dispositivos Samsung Galaxy con arreglos de cámaras múltiples, el proveedor HAL (`vendor.samsung.hardware.camera.provider`) expone la cámara trasera pública como el dispositivo lógico multi-cámara **`ID 0`** (`[2, 5, 6]`) y el modo Foto nativo usa **`device 20`** (`BACK_SEAMLESS_ZOOM` = `[58, 56, 52]`). Al abrir cualquiera de ellos, la función `isAvailableModule()` del driver HAL verifica la EEPROM de los **sensores agrupados simultáneamente**, abortando toda la sesión por la falla del sensor secundario averiado.

El análisis sobre `ShootingModeMap` y el subsistema de cámara demostró que:
1. **Modo Pro (`ShootingMode 3`) y Video Pro (`ShootingMode 36`)** mapean el lente `W` (1x Principal) directamente a **`CameraId.BACK` (`device 56`)** y el lente `T` (3x Telefoto) directamente a **`CameraId.BACK_TELE` (`device 52`)**, inicializando cada sensor de forma individual (`numPhysicalCameras: 1`) sin tocar el sensor Ultra-Wide dañado.
2. **Escáner QR 1x (`ShootingMode 40`, `SCAN_QR_CODE`)** abre directamente **`device 56`** sin superposiciones ni `AccessibilityService` (**arquitectura cero-accesibilidad** desde `v2.1.0`).
3. **Visor Nativo `Camera2` Universal (`UniversalCameraController`):** opera directamente sobre sensores frontales (`ID 1` / `ID 3`) y sensores traseros públicos operativos (cuando no están bloqueados por el HAL) con foto JPEG, video MP4 transaccional y controles manuales/asistidos completos.

Documentación técnica completa:
- [Diagnóstico de Hardware y Subsistema HAL (`docs/hardware-diagnosis.md`)](docs/hardware-diagnosis.md)
- [Arquitectura de Software y Seguridad (`docs/architecture.md`)](docs/architecture.md)
- [Hoja de Ruta y Versiones (`docs/ROADMAP.md`)](docs/ROADMAP.md)
- [Preparación Técnica Google Play Store (`docs/play-store-readiness.md`)](docs/play-store-readiness.md)
- [Política de Privacidad Estática (`docs/privacy-policy.html`)](docs/privacy-policy.html)

---

## ✨ Características Principales (`v2.1.0` — Play Store Readiness)

1. **Cumplimiento Técnico Integral de Google Play Store (`PlayStoreCompliancePolicy`):**
   - **Branding libre de marcas registradas:** título público `Rescue Cam: Sensor Recovery`, `applicationId = io.github.benedetto597.rescuecam`, rutas neutrales `Pictures/RescueCam` y `Movies/RescueCam`, y descargo de responsabilidad visible en la app y documentación.
   - **Arquitectura Cero-Accesibilidad:** eliminación completa de `RearCaptureService` y `BIND_ACCESSIBILITY_SERVICE`.
   - **Android 16 (`targetSdk = 36`) & Edge-to-Edge:** manejo seguro de `WindowInsets` (`systemBars | displayCutout`), soporte adaptativo sin bloqueo portrait forzado para plegables/pantallas grandes y minificación R8/ProGuard en release.
   - **Privacidad y Data Safety 100% offline:** tarjeta integrada en UI + diálogo local de Política de Privacidad y Seguridad de Datos, acompañada de `docs/privacy-policy.html` para GitHub Pages.
2. **Visor Nativo `Camera2` Universal (`UniversalCameraController` + `CameraViewerMath` + `ViewfinderGridOverlay`):**
   - Reutilizable para **sensores frontales y traseros públicos operativos** (`DynamicUiBuilder.getSelectablePublicViewerSensors`), con respaldo automático a cámara frontal si un sensor trasero público dispara error HAL.
   - **Zoom digital seguro:** usa `CONTROL_ZOOM_RATIO` en API 30+ cuando el hardware lo soporta, con respaldo automático a `SCALER_CROP_REGION`.
   - **Controles de captura en vivo:** compensación de exposición (**EV**), **flash / linterna** (`Off`, `Auto`, `On`, `Linterna`), **temporizador** (`Off`, `3s`, `5s`, `10s`) con cuenta regresiva y **cuadrícula de encuadre** (`Sin guía`, `3x3`, `Cruz`).
   - **Apertura variable mecánica (`LENS_APERTURE`):** conmutación $f/1.5 \leftrightarrow f/2.4 \leftrightarrow \text{Auto}$ en modelos compatibles (Galaxy S9/S10/Note 9–10).
3. **Grabación de Video MP4 Transaccional (`MediaRecorder` + `MediaStoreSaver` + `VideoRecordingState`):**
   - Guarda archivos `.mp4` en `Movies/RescueCam` y fotos `.jpg` en `Pictures/RescueCam` con bandera atómica `IS_PENDING` (sin `INTERNET` ni permisos amplios de almacenamiento).
   - Solicita `RECORD_AUDIO` solo al iniciar grabación de video; si se deniega, **degrada limpiamente a grabación MP4 sin audio**.
4. **Exportación e Importación Offline de Perfiles `.json` vía SAF (`DiagnosticProfileCodec`):**
   - Respalda y restaura perfiles de diagnóstico y visor mediante Storage Access Framework sin requerir permisos de almacenamiento ni red.
5. **Mapeador Heurístico `ShootingModeMap`, Asistente One UI & Recuperación Rápida:**
   - Catálogo `S9–S25 / FE / Note / Z / A`, detección de One UI (`2.x`–`8+`), guía paso a paso (`OneUiAssistantGuide`) y recuperación escalonada en 4 etapas (`CameraRecoveryEngine`).

---

## 🛠️ Compilación y Pruebas en Local (Costo $0)

### Ejecutar pruebas unitarias (11 suites en Java puro)
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-local.ps1
```

### Compilar, firmar y verificar el APK en local
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-local.ps1
```
El APK verificado (`apksigner verify` + `aapt2 dump badging`) se genera en `dist/camera-rescue-v2.1.0.apk`.

### Compilar AAB Release firmado para Google Play (sin secretos en Git)
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-aab-local.ps1
```

### Instalar en el dispositivo vía ADB
```powershell
adb install -r .\dist\camera-rescue-v2.1.0.apk
```

---

## 🚀 Publicación con GitHub Actions ($0 Costo Automático)

- **APK en GitHub Releases (`.github/workflows/release-apk.yml`):** se ejecuta **únicamente al enviar un tag `v*`**:
  ```bash
  git tag v2.1.0
  git push origin v2.1.0
  ```
- **AAB Release para Google Play (`.github/workflows/build-play-aab.yml`):** se ejecuta **únicamente bajo demanda manual (`workflow_dispatch`)** usando GitHub Secrets y limpiando keystores temporales en `if: always()`.
