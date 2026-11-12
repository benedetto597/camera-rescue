# Motor de Diagnóstico & Escáner On-Device (`v1.2.0`) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir, probar y desplegar la versión `v1.2.0` de `Camera Rescue` (`com.s23plus.camera`) introduciendo el motor de detección de modelos Samsung (`SamsungModelDatabase`), el escáner de sensores no bloqueante en dispositivo (*Canary Testing*) (`SensorScanner`) y la interfaz visual de diagnóstico en `MainActivity`.

**Architecture:**
1. **`SamsungModelDatabase`**: Diccionario estático de series Samsung Galaxy (S9 a S25 Ultra, Note, Z Fold/Flip y Serie A) que asocia `Build.MODEL` con sus sensores físicos e IDs de bypass conocidos.
2. **`SensorHealth` & `DiscoveredSensor`**: Modelos inmutables para representar el estado de salud de cada lente (`HEALTHY`, `DAMAGED`, `HAL_PRIVATE`).
3. **`SensorScanner`**: Controlador en segundo plano (`HandlerThread`) que abre secuencialmente cada cámara sin bloquear la UI para aislar fallas físicas de bus I2C/EEPROM.
4. **`MainActivity`**: Tarjeta interactiva de diagnóstico con botón **`🔍 Escanear Sensores de este Galaxy`** y reporte detallado de estado.

**Tech Stack:** Java 17, Android SDK 34 (Min SDK 30 / Target SDK 34), `Camera2` API, Gradle Kotlin DSL, PowerShell scripts.

---

### Task 1: Catálogo Heurístico Samsung (`SamsungModelDatabase`) y Pruebas Unitarias

**Files:**
- Create: `app/src/main/java/com/s23plus/camera/SamsungModelDatabase.java`
- Create: `app/src/test/java/com/s23plus/camera/SamsungModelDatabaseTest.java`
- Modify: `scripts/test-local.ps1`

- [ ] **Step 1: Escribir pruebas unitarias en `SamsungModelDatabaseTest.java` verificando identificación de modelos S23+, S24 Ultra, S22, S21, S20, Note 20, Z Fold, A54 y fallback para dispositivos desconocidos**
- [ ] **Step 2: Ejecutar `test-local.ps1` y observar fallo en rojo (RED)**
- [ ] **Step 3: Implementar `SamsungModelDatabase.java` y verificar pase en verde (GREEN)**
- [ ] **Step 4: Commit**

---

### Task 2: Modelos de Datos del Diagnóstico (`SensorHealth` & `DiscoveredSensor`)

**Files:**
- Create: `app/src/main/java/com/s23plus/camera/SensorHealth.java`
- Create: `app/src/main/java/com/s23plus/camera/DiscoveredSensor.java`
- Modify: `app/src/test/java/com/s23plus/camera/SamsungModelDatabaseTest.java`

- [ ] **Step 1: Implementar `SensorHealth.java` y `DiscoveredSensor.java`**
- [ ] **Step 2: Añadir pruebas unitarias de formateo de estado en `SamsungModelDatabaseTest.java` y verificar que la suite completa pasa**
- [ ] **Step 3: Commit**

---

### Task 3: Motor de Escaneo On-Device No Bloqueante (`SensorScanner`)

**Files:**
- Create: `app/src/main/java/com/s23plus/camera/SensorScanner.java`

- [ ] **Step 1: Implementar `SensorScanner.java` con ejecución en `HandlerThread`, prueba segura con timeout, captura de excepciones HAL y callbacks de progreso/resultado**
- [ ] **Step 2: Compilar las clases Java con el SDK de Android**
- [ ] **Step 3: Commit**

---

### Task 4: Integración en `MainActivity`, Actualización de Versión y Compilación del APK v1.2.0

**Files:**
- Modify: `app/src/main/java/com/s23plus/camera/CameraConfig.java`
- Modify: `app/src/test/java/com/s23plus/camera/CameraConfigTest.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/s23plus/camera/MainActivity.java`
- Modify: `scripts/build-local.ps1`

- [ ] **Step 1: Actualizar versión a 1.2.0 (`versionCode = 3`, `versionName = "1.2.0"`) en `CameraConfig`, `CameraConfigTest`, `AndroidManifest.xml` y `app/build.gradle.kts`**
- [ ] **Step 2: Diseñar e integrar la sección visual de diagnóstico con botón de escaneo y tabla de resultados en `MainActivity.java`**
- [ ] **Step 3: Ejecutar pruebas unitarias y compilar el APK final firmado `dist/camera-rescue-v1.2.0.apk` mediante `scripts/build-local.ps1`**
- [ ] **Step 4: Commit**

---

### Task 5: Documentación y Publicación del Tag v1.2.0 en GitHub

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Actualizar `README.md` y `docs/ROADMAP.md` documentando las nuevas capacidades diagnósticas de v1.2.0**
- [ ] **Step 2: Commit, push a `main` y enviar el tag `v1.2.0` a GitHub**
