package com.s23plus.camera;

/**
 * Pruebas unitarias para el catalogo heuristico de modelos Samsung Galaxy.
 */
public final class SamsungModelDatabaseTest {

    public static void main(String[] args) {
        testS23PlusIdentification();
        testS24UltraIdentification();
        testS22SeriesIdentification();
        testS21AndS20Identification();
        testNoteAndFoldIdentification();
        testSeriesAIdentification();
        testUnknownDeviceFallback();
        testDiscoveredSensorFormatting();
        System.out.println("PASS: Todas las pruebas unitarias de SamsungModelDatabaseTest finalizaron con exito (8/8).");
    }

    private static void testDiscoveredSensorFormatting() {
        DiscoveredSensor sensor = new DiscoveredSensor(
                "56",
                "Trasera Principal 1x",
                0,
                4080,
                3060,
                5.4f,
                1.8f,
                SensorHealth.HEALTHY,
                "OK - Stream verificado"
        );
        assertEquals("56", sensor.id, "ID de sensor incorrecto");
        assertEquals("12.5 MP", sensor.getResolutionMegaPixels(), "Calculo de MP incorrecto");
        assertEquals("🟢 Operativo", sensor.health.displayName, "Badge de salud incorrecto");
        assertTrue(sensor.getFormattedSummary().contains("Trasera Principal 1x"), "Resumen debe contener el nombre");
    }

    private static void testS23PlusIdentification() {
        SamsungModelDatabase.ModelProfile profile = SamsungModelDatabase.lookup("SM-S916U1");
        assertEquals("Samsung Galaxy S23+", profile.marketingName, "Nombre S23+ incorrecto");
        assertEquals("S23", profile.series, "Serie S23 incorrecta");
        assertEquals("56", profile.rearMainStandaloneId, "ID trasero principal de S23+ debe ser 56");
        assertEquals("52", profile.rearTeleStandaloneId, "ID trasero telefoto de S23+ debe ser 52");
        assertTrue(profile.hasUltraWideFaultProneHistory, "S23+ debe tener historial de fallo de ultra-wide documentado");
    }

    private static void testS24UltraIdentification() {
        SamsungModelDatabase.ModelProfile profile = SamsungModelDatabase.lookup("SM-S928B");
        assertEquals("Samsung Galaxy S24 Ultra", profile.marketingName, "Nombre S24 Ultra incorrecto");
        assertEquals("S24", profile.series, "Serie S24 incorrecta");
        assertTrue(profile.rearMainStandaloneId != null && !profile.rearMainStandaloneId.isEmpty(), "S24 Ultra debe tener ID principal");
    }

    private static void testS22SeriesIdentification() {
        SamsungModelDatabase.ModelProfile profile = SamsungModelDatabase.lookup("SM-S908U");
        assertEquals("Samsung Galaxy S22 Ultra", profile.marketingName, "Nombre S22 Ultra incorrecto");
        assertEquals("S22", profile.series, "Serie S22 incorrecta");
    }

    private static void testS21AndS20Identification() {
        SamsungModelDatabase.ModelProfile s21 = SamsungModelDatabase.lookup("SM-G998B");
        assertEquals("Samsung Galaxy S21 Ultra", s21.marketingName, "Nombre S21 Ultra incorrecto");

        SamsungModelDatabase.ModelProfile s20 = SamsungModelDatabase.lookup("SM-G981U");
        assertEquals("Samsung Galaxy S20 5G", s20.marketingName, "Nombre S20 incorrecto");
    }

    private static void testNoteAndFoldIdentification() {
        SamsungModelDatabase.ModelProfile note20 = SamsungModelDatabase.lookup("SM-N986U");
        assertEquals("Samsung Galaxy Note 20 Ultra", note20.marketingName, "Nombre Note 20 Ultra incorrecto");

        SamsungModelDatabase.ModelProfile fold5 = SamsungModelDatabase.lookup("SM-F946B");
        assertEquals("Samsung Galaxy Z Fold 5", fold5.marketingName, "Nombre Z Fold 5 incorrecto");
    }

    private static void testSeriesAIdentification() {
        SamsungModelDatabase.ModelProfile a54 = SamsungModelDatabase.lookup("SM-A546B");
        assertEquals("Samsung Galaxy A54 5G", a54.marketingName, "Nombre A54 incorrecto");
        assertEquals("Galaxy A", a54.series, "Serie A54 incorrecta");
    }

    private static void testUnknownDeviceFallback() {
        SamsungModelDatabase.ModelProfile fallback = SamsungModelDatabase.lookup("UNKNOWN_DEVICE_XYZ");
        assertTrue(fallback != null, "Fallback no debe ser nulo");
        assertEquals("Samsung Galaxy (Generico)", fallback.marketingName, "Nombre de fallback generico esperado");
        assertEquals("Generico", fallback.series, "Serie generica esperada");
        assertFalse(fallback.isVerifiedProfile, "Perfil desconocido no debe estar verificado");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " | Esperado: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
}
