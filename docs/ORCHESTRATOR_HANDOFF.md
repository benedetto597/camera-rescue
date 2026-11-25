# Orchestrator Handoff — Camera Rescue

> **Fuente de verdad del estado actual del proyecto.** Lee este archivo antes de planificar o ejecutar la siguiente fase.

## 1. Estado Actual Verificado

- **Versión liberada:** `v1.4.0` (`versionCode = 5`, `minSdk = 30`, `targetSdk = 34`, `compileSdk = 34`)
- **Rama principal:** `main` (sincronizada con `origin/main` sobre `3c6fc74`)
- **Último commit de código/documentación previo al handoff:** `e40217d` (`docs: document v1.4.0 heuristic mapper, One UI assistant, recovery engine, and signed APK`)
- **Tag de lanzamiento:** `v1.4.0`
- **Artefacto APK local verificado:** `dist/camera-rescue-v1.4.0.apk` (`74,956` bytes, firmado v1+v2+v3 con `apksigner`)
- **Estado de pruebas unitarias (`scripts/test-local.ps1`):** **53/53 PASS** en **7 suites puras de Java**:
  - `CameraConfigTest`: `8/8` PASS
  - `SamsungModelDatabaseTest`: `14/14` PASS
  - `ShootingModeMapTest`: `7/7` PASS
  - `OneUiAssistantGuideTest`: `6/6` PASS
  - `CameraRecoveryEngineTest`: `5/5` PASS
  - `SensorSelectionStateTest`: `5/5` PASS
  - `DynamicUiBuilderTest`: `8/8` PASS
- **Estado de compilación (`scripts/build-local.ps1`):** Exit code `0` (`aapt2` + `javac -source 17 -target 17` + `d8` + `zipalign` + `apksigner`)

---

## 2. Resumen de Entregables Completados en `v1.4.0` (Fase 2)

| Commit | Alcance | Archivos Clave |
| :--- | :--- | :--- |
| `c49c391` | Constantes `v1.4.0`, catálogo ampliado `S9–S25/FE/Note/Fold/Flip/A` y motor `ShootingModeMap` (TDD) | `CameraConfig.java`, `SamsungModelDatabase.java`, `ShootingModeMap.java`, `CameraConfigTest.java`, `SamsungModelDatabaseTest.java`, `ShootingModeMapTest.java`, `scripts/test-local.ps1` |
| `004437a` | Asistente guiado `OneUiAssistantGuide` y motor de recuperación `CameraRecoveryEngine` (TDD) | `OneUiAssistantGuide.java`, `CameraRecoveryEngine.java`, `OneUiAssistantGuideTest.java`, `CameraRecoveryEngineTest.java`, `scripts/test-local.ps1` |
| `5f32231` | Puente Android `OneUiSystemBridge`, persistencia de pasos en `RescuePreferences` y manifiesto `v1.4.0` | `OneUiSystemBridge.java`, `RescuePreferences.java`, `AndroidManifest.xml` |
| `1fe5ae9` | Integración completa en `MainActivity` (`ShootingModeMap`, detector de retorno rápido, Recuperación Rápida, Asistente One UI colapsable, Tile `ACTION_QUICK_RECOVERY`) y sync Gradle/Scripts/CI | `MainActivity.java`, `app/build.gradle.kts`, `scripts/build-local.ps1`, `.github/workflows/release-apk.yml` |
| `e40217d` | Documentación `v1.4.0`, plan de implementación y APK firmado | `README.md`, `docs/ROADMAP.md`, `docs/architecture.md`, `docs/superpowers/plans/2026-11-25-v1.4.0-heuristic-mapper-oneui-assistant.md`, `dist/camera-rescue-v1.4.0.apk` |

---

## 3. Decisiones Técnicas y Reglas de Arquitectura Vigentes

1. **Compatibilidad retroactiva en `SamsungModelDatabase.ModelProfile`:**
   - Se conserva el constructor de 9 parámetros delegando al de 13 parámetros (`supportsNativeProVideo`, `hasVariableAperture`, `hasFoldableHingeRisk`, `hardwareNotes`).
   - En Galaxy Note 9 (`SM-N960`), `rearTeleStandaloneId` se corrigió de `"1"` a `"52"` para evitar colisión con `frontWideId = "1"`.
2. **Detección de generación One UI y fallback (`ShootingModeMap` + `OneUiSystemBridge`):**
   - `OneUiSystemBridge.detectDeviceOneUiGeneration()` lee `Build.VERSION.SDK_INT` y por reflexión `Build.VERSION.SEM_PLATFORM_INT`, resolviendo `ONE_UI_2`..`ONE_UI_8_PLUS` con respaldo por API level.
   - `ShootingModeMap.resolve(...)` entrega variantes ordenadas (`Pro`, `ProMode`, `ProVideo`, `SCAN_QR_CODE`) con respaldo explícito a Modo Pro cuando `supportsNativeProVideo == false` o en One UI 2.x.
3. **Recuperación rápida determinista sin doble incremento (`MainActivity` + `CameraRecoveryEngine`):**
   - `onResume()` consume `lastRearLaunchTimestampMs` atómicamente (lo limpia a `0L` de inmediato) y evalúa `isSuspectedCrashReturn(...)` (`≤ 2500 ms`).
   - El flag `recoveryAttemptAdvancedOnLaunch` impide avanzar dos etapas seguidas cuando una recuperación programada vuelve en crash rápido, preservando el escalado `0 -> 1 -> 2 -> 3` (`FORCE_PRO_PRIMARY` → `FORCE_PRO_ALTERNATE` → `FORCE_QR_SINGLE_SENSOR` → `OPEN_CAMERA_APP_INFO`).
   - Durante el enfriamiento HAL (`350 ms`, `pendingRecoveryRunnable != null`), `onResume()` evita reabrir la cámara frontal `Camera2` para no interferir con el reinicio del proveedor HAL.
4. **Cero dependencias externas & 100% offline:**
   - Sin librerías de terceros; 7 suites unitarias en Java puro sin JUnit; sin `android.permission.INTERNET`.
   - `RearCaptureService` y el paquete `com.s23plus.camera` permanecen intactos en `v1.4.0` (su evolución/reestructuración corresponde a Fases 3 y 4).

---

## 4. Hitos Pendientes del Roadmap

1. **SIGUIENTE — Fase 3 (`v2.0.0`): Camera Rescue Universal**
   - Visor nativo `Camera2` completo (tanto sensores frontales como traseros públicos operativos) con zoom digital seguro (`SCALER_CROP_REGION` / `CONTROL_ZOOM_RATIO` en API 30+), controles de captura (EV, flash/linterna, temporizador, cuadrícula) y soporte para apertura variable mecánica ($f/1.5 \leftrightarrow f/2.4$) en S9/S10/Note 9–10 (`LENS_APERTURE`).
   - Grabación de video MP4 transaccional en `MediaStore` (`MediaRecorder` + `MediaStore.Video.Media` con `IS_PENDING`).
   - Exportación/importación local offline de perfiles de diagnóstico y configuración en `.json` (SAF `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`, sin permisos de almacenamiento amplio ni `INTERNET`).
2. **Fase 4 (`Lanzamiento Producción`): Play Store Readiness**
   - Cumplimiento integral de políticas de tienda (branding/paquete libre de marcas registradas, arquitectura limpia respecto a `AccessibilityService` o cumplimiento documentado, `targetSdk` requerido, política de privacidad, firma de release/AAB segura sin exponer keystores en Git, assets y verificación final).

---

## 5. Siguiente Paso Exacto

1. Leer este handoff (`docs/ORCHESTRATOR_HANDOFF.md`), `docs/ROADMAP.md`, `docs/architecture.md`, `FrontCameraController.java`, `MediaStoreSaver.java`, `SamsungModelDatabase.java` y `MainActivity.java`.
2. Usar `superpowers:writing-plans` para redactar el plan técnico detallado de **Fase 3 (`v2.0.0`)** en `docs/superpowers/plans/`.
3. Ejecutar la Fase 3 (`v2.0.0`) mediante subagentes frescos (`implementer` + `reviewer`) con TDD estricto en toda lógica pura (cálculos de zoom/crop `Camera2`, mapeo de apertura variable, serialización/validación de perfiles `.json` y estados de grabación), verificación real de `scripts/test-local.ps1` y `scripts/build-local.ps1` (`dist/camera-rescue-v2.0.0.apk`), actualización de documentación, tag `v2.0.0` y actualización de este handoff antes de llegar a ~60% de contexto.
