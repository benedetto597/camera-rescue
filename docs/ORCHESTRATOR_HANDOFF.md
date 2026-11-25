# Orchestrator Handoff — Rescue Cam: Sensor Recovery

> **Fuente de verdad del estado actual del proyecto.** Lee este archivo antes de planificar o ejecutar la siguiente fase.

## 1. Estado Actual Verificado

- **Versión liberada:** `v2.1.0` (`versionCode = 7`, `applicationId = io.github.benedetto597.rescuecam`, `namespace = com.s23plus.camera`, `minSdk = 30`, `targetSdk = 36`, `compileSdk = 36`)
- **Título público:** `Rescue Cam: Sensor Recovery` (`27` chars, nombre corto `Rescue Cam`)
- **Rama principal:** `main` (sincronizada con `origin/main` y tag `v2.1.0`)
- **Último commit de código/documentación previo al handoff:** `2db2e7f` (tras corrección de plan y este handoff)
- **Tag de lanzamiento:** `v2.1.0`
- **Artefacto APK local verificado:** `dist/camera-rescue-v2.1.0.apk` (`111,734` bytes, firmado v1+v2+v3 con `apksigner`, `aapt2 dump badging` verificado: `io.github.benedetto597.rescuecam`, `versionCode=7`, `versionName=2.1.0`, `targetSdkVersion=36`, 0 permisos prohibidos)
- **Estado de pruebas unitarias (`scripts/test-local.ps1`):** **92/92 PASS** en **11 suites puras de Java** (compiladas sin `android.jar`):
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
  - `PlayStoreCompliancePolicyTest`: `11/11` PASS (incluye validación de contrato real de `AndroidManifest.xml`, `app/build.gradle.kts` y `docs/privacy-policy.html`)
- **Estado de compilación (`scripts/build-local.ps1`):** Exit code `0` (`aapt2` 36.0.0 + `javac -source 17 -target 17` + `d8` + `zipalign` + `apksigner` + `apksigner verify` + `aapt2 dump badging`)

---

## 2. Resumen de Entregables Completados en `v2.1.0` (Fase 4 — Play Store Readiness)

| Commit | Alcance | Archivos Clave |
| :--- | :--- | :--- |
| `f03c351` | Branding `v2.1.0` libre de marcas registradas, specs cero-accesibilidad (`REAR_QR_DIRECT`) y motor puro `PlayStoreCompliancePolicy` (TDD) | `CameraConfig.java`, `RescueActionSpec.java`, `DynamicUiBuilder.java`, `OneUiAssistantGuide.java`, `PlayStoreCompliancePolicy.java`, `CameraConfigTest.java`, `DynamicUiBuilderTest.java`, `OneUiAssistantGuideTest.java`, `PlayStoreCompliancePolicyTest.java`, `scripts/test-local.ps1` |
| `3851f46` | Eliminación completa de `RearCaptureService` y `accessibility_service_config.xml`, manifiesto API 36 adaptativo, `WindowInsets` edge-to-edge y tarjeta + diálogo offline de Privacidad/Aviso Legal | `AndroidManifest.xml`, `MainActivity.java`, `MediaStoreSaver.java`, `RescueShortcutManager.java`, `CameraRescueTileService.java` (y eliminación de `RearCaptureService.java`, `res/xml/accessibility_service_config.xml`) |
| `778b505` | Build `compileSdk=36` / `targetSdk=36`, R8/ProGuard, firma release segura sin secretos en Git, verificación `apksigner`/`aapt2`/`jarsigner`, scripts y workflows APK (`v*`) + AAB (`workflow_dispatch`) | `.gitignore`, `build.gradle.kts`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `scripts/build-local.ps1`, `scripts/build-aab-local.ps1`, `.github/workflows/release-apk.yml`, `.github/workflows/build-play-aab.yml` |
| `f437764` | Política de Privacidad estática bilingüe (`docs/privacy-policy.html`), guía técnica `docs/play-store-readiness.md` y test de contrato real del workspace (`11/11`) | `docs/privacy-policy.html`, `docs/play-store-readiness.md`, `PlayStoreCompliancePolicyTest.java` |
| `2db2e7f` | Actualización de documentación técnica `v2.1.0`, plan de implementación y APK verificado `dist/camera-rescue-v2.1.0.apk` | `README.md`, `SECURITY.md`, `docs/ROADMAP.md`, `docs/architecture.md`, `docs/superpowers/plans/2026-11-25-v2.1.0-play-store-readiness.md`, `dist/camera-rescue-v2.1.0.apk` |

---

## 3. Decisiones Técnicas y Reglas de Arquitectura Vigentes

1. **Límite estricto de pruebas en Java puro (`scripts/test-local.ps1`):**
   - `javac` compila `$PureSources` **sin `android.jar`**. Las 11 suites puras (`CameraConfig`, `SamsungModelDatabase`, `ShootingModeMap`, `OneUiAssistantGuide`, `CameraRecoveryEngine`, `SensorSelectionState`, `DynamicUiBuilder`, `CameraViewerMath`, `VideoRecordingState`, `DiagnosticProfileCodec`, `PlayStoreCompliancePolicy`) usan únicamente `java.*`.
2. **Arquitectura Cero-Accesibilidad y Cumplimiento Play Store (`PlayStoreCompliancePolicy`):**
   - `RearCaptureService` y `BIND_ACCESSIBILITY_SERVICE` fueron eliminados completamente. El rescate trasero opera mediante `ShootingMode 3` (Pro), `ShootingMode 36` (Video Pro), `ShootingMode 40` (`REAR_QR_DIRECT` sin overlay) y el visor nativo `Camera2` (`UniversalCameraController`).
   - El manifiesto declara únicamente `CAMERA` y `RECORD_AUDIO` (opcional, degradable a video sin audio), con `android:allowBackup="false"`, cero `INTERNET` y cero permisos amplios de almacenamiento.
3. **Dual Package Alignment (`applicationId != namespace`):**
   - `applicationId` público: `io.github.benedetto597.rescuecam` (libre de marcas registradas).
   - `namespace` interno Java: `com.s23plus.camera` (preserva compatibilidad de perfiles `.json` `schemaVersion = 1` y acciones `com.s23plus.camera.action.*`).
   - En compilaciones directas sin Gradle (`scripts/build-local.ps1` y `release-apk.yml`), `aapt2 link` recibe el manifiesto temporal con `package="com.s23plus.camera"` y `--rename-manifest-package io.github.benedetto597.rescuecam --custom-package com.s23plus.camera`.
4. **Android 16 (`targetSdk = 36`) & Edge-to-Edge:**
   - `MainActivity` aplica `WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()` sin acumular padding y elimina el bloqueo de orientación `screenOrientation="portrait"` para pantallas grandes y plegables (`resizeableActivity="true"`).
   - `MediaStoreSaver` guarda en `Pictures/RescueCam` (JPEG) y `Movies/RescueCam` (MP4) con `IS_PENDING = 1 -> 0`.
5. **Seguridad de Firma y Artefactos Release:**
   - `.gitignore` excluye `*.jks`, `*.keystore`, `*.p12`, `*.pk8`, `*.pem`, `keystore.properties`, `signing.properties` y `*.aab`.
   - `app/build.gradle.kts` habilita R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) y jamás usa firma `debug` en `release`.
   - `.github/workflows/build-play-aab.yml` compila y verifica con `jarsigner -verify` (`jar verified.`) el `.aab` firmado únicamente bajo `workflow_dispatch` con limpieza en `$RUNNER_TEMP`.

---

## 4. Próximos Pasos Opcionales (Operación en Consola)

1. Habilitar **GitHub Pages** en la configuración del repositorio (rama `main` / `/docs`) para servir `https://benedetto597.github.io/camera-rescue/privacy-policy.html`.
2. Generar una Upload Keystore externa fuera del repositorio y configurar los 4 GitHub Secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) o `keystore.properties` local ignorado por Git.
3. Ejecutar manualmente el workflow `.github/workflows/build-play-aab.yml` (o `scripts/build-aab-local.ps1`) para obtener `app-release.aab` firmado y subirlo a Google Play Console (Closed Testing) junto con el formulario Data Safety descrito en `docs/play-store-readiness.md`.
