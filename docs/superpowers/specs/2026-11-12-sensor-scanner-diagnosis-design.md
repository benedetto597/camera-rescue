# Especificación de Diseño: Motor de Diagnóstico & Escáner On-Device (`v1.2.0`)

**Fecha:** 12 de noviembre de 2026  
**Repositorio:** `camera-rescue` (`https://github.com/benedetto597/camera-rescue`)  
**Versión Objetivo:** `v1.2.0` (versionCode 3)  
**Estado:** Aprobado para Implementación

---

## 1. Problema y Objetivos

Actualmente, la versión `v1.0.1` de `Camera Rescue` utiliza IDs estáticos para el Samsung Galaxy S23+ (`ID 1`, `ID 3`, `ID 56`, `ID 52`). En otros dispositivos Samsung (ej. Galaxy S20, S21, S22, S24 o Note), los sensores físicos independientes e identificadores HAL son diferentes.

El objetivo de la **v1.2.0** es implementar:
1. **Detección Automática de Dispositivo (`SamsungModelDatabase`):** Identificar el modelo exacto (`Build.MODEL`) a partir de una base de datos heurística de terminales Galaxy (S9 a S25 Ultra, Note, Z Fold/Flip y Serie A).
2. **Escaneo *Canary* No Bloqueante (`SensorScanner`):** Probar secuencialmente en un hilo de fondo (`HandlerThread`) cada sensor expuesto por el sistema sin congelar la UI y clasificando su estado de salud (`OPERATIVO`, `DAÑADO`, `PRIVADO_HAL`).
3. **Reporte Visual en Pantalla:** Desplegar una ficha diagnóstica en `MainActivity` con los sensores probados, su resolución, focal, apertura y estado de salud.

---

## 2. Arquitectura de Componentes

```
app/src/main/java/com/s23plus/camera/
├── SamsungModelDatabase.java   # Catálogo heurístico de modelos Samsung y asignación de sensores conocidos
├── DiscoveredSensor.java       # Modelo de datos inmutable por sensor (ID, nombre, focal, resolución, estado)
├── SensorHealth.java           # Enum de clasificación (HEALTHY, DAMAGED, HAL_PRIVATE, UNKNOWN)
├── SensorScanner.java          # Motor de prueba secuencial no bloqueante sobre CameraManager
├── CameraConfig.java           # Actualización a v1.2.0 y soporte de modelos
├── MainActivity.java           # Sección visual de diagnóstico y botón de escaneo interactivo
```

---

## 3. Comportamiento del Escáner (*Canary Testing*)

1. **Enumeración:**
   - Consulta `CameraManager.getCameraIdList()` y los metadatos físicos `CameraCharacteristics.getPhysicalCameraIds()`.
2. **Prueba de Apertura Segura:**
   - Para cada cámara, intenta abrirla en segundo plano con un temporizador de seguridad de 800 ms.
   - Si la cámara entra en `onOpened()`, se marca como `HEALTHY` y se cierra de inmediato.
   - Si lanza `CameraAccessException`, o invoca `onError(error)`, o falla por el driver HAL (`CamxResultEFailed`, `-38`), se marca como `DAMAGED` registrando el código de error.
3. **Mapeo con la Base de Datos:**
   - Cruza los IDs probados con el perfil de `SamsungModelDatabase` para identificar qué sensor físico corresponde a cada ID y cuáles son los IDs independientes de rescate (`device 56`, etc.).

---

## 4. Estándares de Seguridad y Calidad

- **TDD:** Cobertura de pruebas unitarias puras para `SamsungModelDatabase` y las estructuras de reporte de diagnóstico en `scripts/test-local.ps1`.
- **0% Permisos de Red:** La aplicación se mantiene estrictamente offline (`INTERNET` no declarado).
- **Compilación Local y CI/CD:** Verificación con `scripts/build-local.ps1` y workflow de GitHub Actions en tags `v*`.
