# Orchestrator Handoff — Camera Rescue

> **Fuente de verdad del estado actual del proyecto.** Lee este archivo antes de planificar o ejecutar la siguiente fase.

## 1. Estado Actual Verificado

- **Versión liberada:** `v2.0.0` (`versionCode = 6`, `minSdk = 30`, `targetSdk = 34`, `compileSdk = 34`)
- **Rama principal:** `main` (sincronizada con `origin/main` sobre `f8ce9a1`)
- **Último commit de código/documentación previo al handoff:** `dc527cd` (`docs: document v2.0.0 universal Camera2 viewer, MP4 recording, SAF JSON profiles, and signed APK`)
- **Tag de lanzamiento:** `v2.0.0`
- **Artefacto APK local verificado:** `dist/camera-rescue-v2.0.0.apk` (`111,820` bytes, firmado v1+v2+v3 con `apksigner`)
- **Estado de pruebas unitarias (`scripts/test-local.ps1`):** **81/81 PASS** en **10 suites puras de Java** (compiladas sin `android.jar`):
  - `CameraConfigTest`: `9/9` PASS
  - `SamsungModelDatabaseTest`: `14/14` PASS
  - `ShootingModeMapTest`: `7/7` PASS
  - `OneUiAssistantGuideTest`: `6/6` PASS
  - `CameraRecoveryEngineTest`: `5/5` PASS
  - `SensorSelectionStateTest`: `5/5` PASS
  - `DynamicUiBuilderTest`: `9/9` PASS
  - `CameraViewerMathTest`: `9/9` PASS
  - `VideoRecordingStateTest`: `6/6` PASS
  - `DiagnosticProfileCodecTest`: `11/11` PASS
- **Estado de compilación (`scripts/build-local.ps1`):** Exit code `0` (`aapt2` + `javac -source 17 -target 17` + `d8` + `zipalign` + `apksigner`)

---

## 2. Resumen de Entregables Completados en `v2.0.0` (Fase 3 — Camera Rescue Universal)

| Commit | Alcance | Archivos Clave |
| :--- | :--- | :--- |
| `c2f987c` | Constantes `v2.0.0`, motor matemático `CameraViewerMath`, máquina de estados `VideoRecordingState` y selectores públicos de visor en `DynamicUiBuilder` (TDD) | `CameraConfig.java`, `CameraViewerMath.java`, `VideoRecordingState.java`, `DynamicUiBuilder.java`, `CameraConfigTest.java`, `DynamicUiBuilderTest.java`, `CameraViewerMathTest.java`, `VideoRecordingStateTest.java`, `scripts/test-local.ps1` |
| `5656328` | Codec JSON RFC-8259 en Java puro `DiagnosticProfileCodec` con validación estricta de esquema, límites y lista blanca de acciones (TDD) | `DiagnosticProfileCodec.java`, `DiagnosticProfileCodecTest.java`, `scripts/test-local.ps1` |
| `1b47f1d` | Controlador unificado `UniversalCameraController`, video MP4 transaccional en `MediaStoreSaver`, `ViewfinderGridOverlay`, preferencias del visor en `RescuePreferences` y manifiesto `v2.0.0` | `UniversalCameraController.java`, `MediaStoreSaver.java`, `ViewfinderGridOverlay.java`, `RescuePreferences.java`, `AndroidManifest.xml` |
| `2876b62` | Fix de concurrencia en `UniversalCameraController` (limpieza de grabación en `onDisconnected`/`onError` y guardia `videoState.isRecordingOrBusy()` en `createPreviewSession`) | `UniversalCameraController.java` |
| `68726f6` | Integración completa en `MainActivity` (visor Camera2 universal, zoom/EV/flash/timer/cuadrícula/apertura variable, MP4 con degradación limpia sin `RECORD_AUDIO`, SAF `.json`), eliminación de `FrontCameraController.java` y sync `v2.0.0` | `MainActivity.java`, `app/build.gradle.kts`, `scripts/build-local.ps1`, `.github/workflows/release-apk.yml` (y eliminación de `FrontCameraController.java`) |
| `85b08a2` | Fix de ciclo de vida en `MainActivity` (diferir inicio MP4 post-permiso `RECORD_AUDIO` hasta sesión `Activa:` en `onResume`, validar `CAMERA` antes de abrir lente y marcar apertura exitosa tras `createPreviewSession`) | `MainActivity.java` |
| `dc527cd` | Documentación `v2.0.0`, plan de implementación y APK firmado | `README.md`, `docs/ROADMAP.md`, `docs/architecture.md`, `docs/superpowers/plans/2026-11-25-v2.0.0-camera-rescue-universal.md`, `dist/camera-rescue-v2.0.0.apk` |

---

## 3. Decisiones Técnicas y Reglas de Arquitectura Vigentes

1. **Límite estricto de pruebas en Java puro (`scripts/test-local.ps1`):**
   - `javac` compila `$PureSources` **sin `android.jar`**. Toda clase incluida en esas 10 suites (`CameraConfig`, `SamsungModelDatabase`, `ShootingModeMap`, `OneUiAssistantGuide`, `CameraRecoveryEngine`, `SensorHealth`, `DiscoveredSensor`, `RescueActionSpec`, `SensorSelectionState`, `DynamicUiBuilder`, `CameraViewerMath`, `VideoRecordingState`, `DiagnosticProfileCodec`) debe usar únicamente `java.*`, nunca `android.*` ni `org.json.*`.
2. **Visor `Camera2` Universal (`UniversalCameraController` + `CameraViewerMath` + `ViewfinderGridOverlay`):**
   - Reemplaza a `FrontCameraController` para operar sensores frontales y sensores traseros públicos con salud `HEALTHY` (`DynamicUiBuilder.getSelectablePublicViewerSensors`).
   - Zoom digital seguro con `CONTROL_ZOOM_RATIO` en API 30+ cuando el sensor lo soporta y respaldo automático a `SCALER_CROP_REGION`.
   - Apertura variable mecánica (`CaptureRequest.LENS_APERTURE`) habilitada solo cuando `LENS_INFO_AVAILABLE_APERTURES` expone ≥ 2 aperturas distintas (`f/1.5` ↔ `f/2.4` en S9/S10/Note 9–10), con retorno automático a `Auto` al cambiar a lentes fijas.
   - Si un sensor trasero público falla al abrir o configurar sesión en el visor integrado, `MainActivity.onError` revierte automáticamente a `FRONT_CAMERA_WIDE_ID` (`"1"`).
3. **Grabación de Video MP4 Transaccional y Degradación de Audio:**
   - `MediaStoreSaver.createPendingVideoSession` escribe en `Movies/S23Camera` (`MediaStore.Video.Media` + `IS_PENDING = 1`, sin permisos de almacenamiento amplio).
   - `MediaRecorder` usa `new MediaRecorder(context)` en API 31+ y helper heredado aislado en API 30 (`minSdk = 30`), descartando clips `< 600 ms` o errores mediante `abortPendingVideo`.
   - `RECORD_AUDIO` se solicita únicamente al grabar video; si se deniega, la app difiere el inicio hasta `onResume()` (`onStatusChanged("Activa:...")`) y graba MP4 limpio en modo `VIDEO_ONLY_NO_MIC_PERMISSION`.
4. **Perfiles Offline `.json` vía SAF (`DiagnosticProfileCodec`):**
   - Exportación (`ACTION_CREATE_DOCUMENT`, apertura `"wt"`) e importación (`ACTION_OPEN_DOCUMENT`, lectura acotada a `MAX_JSON_BYTES + 1`) 100% offline sin `INTERNET` ni permisos de almacenamiento.
   - Valida `schemaVersion == 1`, límites de `256 KB` y `64` sensores, claves JSON duplicadas, tipos y lista blanca de `quickTileAction` para evitar inyección de Intents.
5. **Cero dependencias externas & Seguridad de Repositorio:**
   - Sin librerías de terceros ni `android.permission.INTERNET`.
   - `RearCaptureService` y el paquete `com.s23plus.camera` permanecen intactos hasta **Fase 4 (Play Store Readiness)**, donde se evaluará branding/paquete libre de marcas registradas, estrategia sin `AccessibilityService` o cumplimiento de tienda, `targetSdk` requerido, política de privacidad y pipeline seguro de AAB sin exponer keystores ni documentos de negocio en el repo público.

---

## 4. Hitos Pendientes del Roadmap

1. **SIGUIENTE — Fase 4 (`Lanzamiento Producción`): Play Store Readiness**
   - Revisar criterios de tienda (branding/título/descripción sin infracción de marcas registradas, decisión de paquete y estrategia cero-accesibilidad o cumplimiento documentado, `targetSdk` vigente requerido por Google Play, política de privacidad estática, sección Data Safety 100% offline, firma release `.aab` segura sin subir keystores ni documentos privados de negocio al repo público, y verificación integral de pruebas y compilación).

---

## 5. Siguiente Paso Exacto

1. Leer este handoff (`docs/ORCHESTRATOR_HANDOFF.md`), `docs/ROADMAP.md`, `docs/architecture.md`, `AndroidManifest.xml`, `app/build.gradle.kts`, `RearCaptureService.java`, `MainActivity.java` y (solo como lectura privada sin copiar ni citar datos confidenciales en el repo público) `C:\Users\bened\Documents\Repositorios\camera-s23-plus\MONETIZATION_PLAN.md`.
2. Usar `superpowers:writing-plans` para redactar el plan técnico de **Fase 4 (Play Store Readiness)** en `docs/superpowers/plans/`.
3. Ejecutar Fase 4 mediante subagentes frescos (`implementer` + `reviewer`) con TDD estricto en lógica pura, verificación real de `scripts/test-local.ps1` y `scripts/build-local.ps1` (y/o verificación Gradle release/AAB según corresponda), actualización de documentación, tag correspondiente y actualización de este handoff antes de llegar a ~60% de contexto.
