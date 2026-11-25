package com.s23plus.camera;

/**
 * Pruebas unitarias para el catalogo heuristico de modelos Samsung Galaxy.
 */
public final class SamsungModelDatabaseTest {

    public static void main(String[] args) {
        testS23PlusIdentification();
        testS24UltraIdentification();
        testS25SeriesAndEdgeIdentification();
        testFeModelsIdentification();
        testS22SeriesIdentification();
        testS21AndS20Identification();
        testS10AndS9VariableApertureIdentification();
        testNoteAndFoldIdentification();
        testNote9TelephotoDoesNotCollideWithFrontId();
        testFoldableHingeRiskAndFlipModels();
        testSeriesAIdentification();
        testModelProfileConstructorBackwardsCompatibility();
        testUnknownDeviceFallback();
        testDiscoveredSensorFormatting();
        System.out.println("PASS: Todas las pruebas unitarias de SamsungModelDatabaseTest finalizaron con exito (14/14).");
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
        assertTrue(profile.supportsNativeProVideo, "S24 Ultra debe soportar Pro Video nativo");
        assertFalse(profile.hasVariableAperture, "S24 Ultra no tiene apertura variable");
        assertFalse(profile.hasFoldableHingeRisk, "S24 Ultra no es plegable");
        assertTrue(profile.hardwareNotes != null && !profile.hardwareNotes.isEmpty(), "S24 Ultra debe tener hardwareNotes");
    }

    private static void testS25SeriesAndEdgeIdentification() {
        SamsungModelDatabase.ModelProfile s25Ultra = SamsungModelDatabase.lookup("SM-S938B");
        assertEquals("Samsung Galaxy S25 Ultra", s25Ultra.marketingName, "Nombre S25 Ultra incorrecto");
        assertEquals("S25", s25Ultra.series, "Serie S25 incorrecta");
        assertEquals("56", s25Ultra.rearMainStandaloneId, "S25 Ultra ID principal incorrecto");
        assertEquals("52", s25Ultra.rearTeleStandaloneId, "S25 Ultra ID telefoto incorrecto");
        assertEquals("1", s25Ultra.frontWideId, "S25 Ultra ID frontal wide incorrecto");
        assertEquals("3", s25Ultra.frontNormalId, "S25 Ultra ID frontal normal incorrecto");
        assertTrue(s25Ultra.hasUltraWideFaultProneHistory, "S25 Ultra debe marcar riesgo UW");
        assertTrue(s25Ultra.isVerifiedProfile, "S25 Ultra debe estar verificado");
        assertTrue(s25Ultra.supportsNativeProVideo, "S25 Ultra debe soportar Pro Video");
        assertFalse(s25Ultra.hasVariableAperture, "S25 Ultra no tiene apertura variable");
        assertFalse(s25Ultra.hasFoldableHingeRisk, "S25 Ultra no es plegable");

        SamsungModelDatabase.ModelProfile s25Plus = SamsungModelDatabase.lookup("SM-S936U");
        assertEquals("Samsung Galaxy S25+", s25Plus.marketingName, "Nombre S25+ incorrecto");

        SamsungModelDatabase.ModelProfile s25 = SamsungModelDatabase.lookup("SM-S931B");
        assertEquals("Samsung Galaxy S25", s25.marketingName, "Nombre S25 incorrecto");

        SamsungModelDatabase.ModelProfile s25Edge = SamsungModelDatabase.lookup("SM-S937U");
        assertEquals("Samsung Galaxy S25 Edge", s25Edge.marketingName, "Nombre S25 Edge incorrecto");
        assertEquals("56", s25Edge.rearMainStandaloneId, "S25 Edge ID principal incorrecto");
        assertEquals(null, s25Edge.rearTeleStandaloneId, "S25 Edge no debe tener telefoto");
        assertTrue(s25Edge.supportsNativeProVideo, "S25 Edge debe soportar Pro Video");
    }

    private static void testFeModelsIdentification() {
        SamsungModelDatabase.ModelProfile s24Fe = SamsungModelDatabase.lookup("SM-S721B");
        assertEquals("Samsung Galaxy S24 FE", s24Fe.marketingName, "Nombre S24 FE incorrecto");
        assertEquals("S24", s24Fe.series, "Serie S24 FE incorrecta");
        assertEquals("52", s24Fe.rearTeleStandaloneId, "S24 FE debe tener telefoto 52");

        SamsungModelDatabase.ModelProfile s23Fe = SamsungModelDatabase.lookup("SM-S711U");
        assertEquals("Samsung Galaxy S23 FE", s23Fe.marketingName, "Nombre S23 FE incorrecto");
        assertEquals("S23", s23Fe.series, "Serie S23 FE incorrecta");

        SamsungModelDatabase.ModelProfile s21Fe = SamsungModelDatabase.lookup("SM-G990E");
        assertEquals("Samsung Galaxy S21 FE 5G", s21Fe.marketingName, "Nombre S21 FE incorrecto");
        assertEquals("S21", s21Fe.series, "Serie S21 FE incorrecta");

        SamsungModelDatabase.ModelProfile s20Fe = SamsungModelDatabase.lookup("SM-G781B");
        assertEquals("Samsung Galaxy S20 FE", s20Fe.marketingName, "Nombre S20 FE incorrecto");
        assertEquals("S20", s20Fe.series, "Serie S20 FE incorrecta");
        assertEquals("52", s20Fe.rearTeleStandaloneId, "S20 FE debe tener telefoto 52");
        assertEquals(null, s20Fe.frontNormalId, "S20 FE frontNormalId debe ser null");
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
        assertTrue(note20.supportsNativeProVideo, "Note 20 Ultra debe soportar Pro Video");
        assertFalse(note20.hasVariableAperture, "Note 20 Ultra no tiene apertura variable");

        SamsungModelDatabase.ModelProfile note10Plus = SamsungModelDatabase.lookup("SM-N975F");
        assertEquals("Samsung Galaxy Note 10+", note10Plus.marketingName, "Nombre Note 10+ incorrecto");
        assertTrue(note10Plus.supportsNativeProVideo, "Note 10+ debe soportar Pro Video");
        assertTrue(note10Plus.hasVariableAperture, "Note 10+ tiene apertura variable");

        SamsungModelDatabase.ModelProfile fold5 = SamsungModelDatabase.lookup("SM-F946B");
        assertEquals("Samsung Galaxy Z Fold 5", fold5.marketingName, "Nombre Z Fold 5 incorrecto");
        assertTrue(fold5.hasFoldableHingeRisk, "Z Fold 5 debe marcar riesgo de bisagra");
    }

    private static void testS10AndS9VariableApertureIdentification() {
        SamsungModelDatabase.ModelProfile s105g = SamsungModelDatabase.lookup("SM-G977B");
        assertEquals("Samsung Galaxy S10 5G", s105g.marketingName, "Nombre S10 5G incorrecto");
        assertTrue(s105g.supportsNativeProVideo, "S10 5G debe soportar Pro Video");
        assertTrue(s105g.hasVariableAperture, "S10 5G tiene apertura variable");

        SamsungModelDatabase.ModelProfile s10e = SamsungModelDatabase.lookup("SM-G970F");
        assertEquals("Samsung Galaxy S10e", s10e.marketingName, "Nombre S10e incorrecto");
        assertEquals(null, s10e.rearTeleStandaloneId, "S10e no tiene telefoto");
        assertEquals(null, s10e.frontNormalId, "S10e frontNormalId debe ser null");
        assertTrue(s10e.hasVariableAperture, "S10e tiene apertura variable");

        SamsungModelDatabase.ModelProfile s9Plus = SamsungModelDatabase.lookup("SM-G965F");
        assertEquals("Samsung Galaxy S9+", s9Plus.marketingName, "Nombre S9+ incorrecto");
        assertEquals("0", s9Plus.rearMainStandaloneId, "S9+ ID principal debe ser 0");
        assertEquals("52", s9Plus.rearTeleStandaloneId, "S9+ ID telefoto debe ser 52");
        assertFalse(s9Plus.supportsNativeProVideo, "S9+ no soporta Pro Video nativo");
        assertTrue(s9Plus.hasVariableAperture, "S9+ tiene apertura variable");

        SamsungModelDatabase.ModelProfile s9 = SamsungModelDatabase.lookup("SM-G960F");
        assertEquals("Samsung Galaxy S9", s9.marketingName, "Nombre S9 incorrecto");
        assertFalse(s9.supportsNativeProVideo, "S9 no soporta Pro Video nativo");
        assertTrue(s9.hasVariableAperture, "S9 tiene apertura variable");
    }

    private static void testNote9TelephotoDoesNotCollideWithFrontId() {
        SamsungModelDatabase.ModelProfile note9 = SamsungModelDatabase.lookup("SM-N960U");
        assertEquals("Samsung Galaxy Note 9", note9.marketingName, "Nombre Note 9 incorrecto");
        assertEquals("0", note9.rearMainStandaloneId, "Note 9 ID principal debe ser 0");
        assertEquals("52", note9.rearTeleStandaloneId, "Note 9 ID telefoto debe ser 52");
        assertEquals("1", note9.frontWideId, "Note 9 ID frontal debe ser 1");
        assertTrue(!note9.rearTeleStandaloneId.equals(note9.frontWideId),
                "Note 9 telefoto no debe colisionar con frontWideId");
        assertFalse(note9.supportsNativeProVideo, "Note 9 no soporta Pro Video nativo");
        assertTrue(note9.hasVariableAperture, "Note 9 tiene apertura variable");
    }

    private static void testFoldableHingeRiskAndFlipModels() {
        SamsungModelDatabase.ModelProfile fold6 = SamsungModelDatabase.lookup("SM-F956B");
        assertEquals("Samsung Galaxy Z Fold 6", fold6.marketingName, "Nombre Z Fold 6 incorrecto");
        assertTrue(fold6.hasFoldableHingeRisk, "Z Fold 6 debe tener hasFoldableHingeRisk");
        assertTrue(fold6.supportsNativeProVideo, "Z Fold 6 debe soportar Pro Video");

        SamsungModelDatabase.ModelProfile fold1 = SamsungModelDatabase.lookup("SM-F900F");
        assertEquals("Samsung Galaxy Fold", fold1.marketingName, "Nombre Galaxy Fold incorrecto");
        assertTrue(fold1.hasFoldableHingeRisk, "Galaxy Fold debe tener hasFoldableHingeRisk");

        SamsungModelDatabase.ModelProfile flip6 = SamsungModelDatabase.lookup("SM-F741B");
        assertEquals("Samsung Galaxy Z Flip 6", flip6.marketingName, "Nombre Z Flip 6 incorrecto");
        assertEquals(null, flip6.rearTeleStandaloneId, "Z Flip 6 no tiene telefoto");
        assertTrue(flip6.hasFoldableHingeRisk, "Z Flip 6 debe tener hasFoldableHingeRisk");
        assertTrue(flip6.supportsNativeProVideo, "Z Flip 6 debe soportar Pro Video");

        SamsungModelDatabase.ModelProfile flip1 = SamsungModelDatabase.lookup("SM-F700F");
        assertEquals("Samsung Galaxy Z Flip", flip1.marketingName, "Nombre Z Flip original incorrecto");
        assertTrue(flip1.hasFoldableHingeRisk, "Z Flip original debe tener hasFoldableHingeRisk");
        assertFalse(flip1.supportsNativeProVideo, "Z Flip original no soporta Pro Video nativo");
    }

    private static void testSeriesAIdentification() {
        SamsungModelDatabase.ModelProfile a54 = SamsungModelDatabase.lookup("SM-A546B");
        assertEquals("Samsung Galaxy A54 5G", a54.marketingName, "Nombre A54 incorrecto");
        assertEquals("Galaxy A", a54.series, "Serie A54 incorrecta");
        assertFalse(a54.supportsNativeProVideo, "A54 no soporta Pro Video nativo");

        SamsungModelDatabase.ModelProfile a72 = SamsungModelDatabase.lookup("SM-A725F");
        assertEquals("Samsung Galaxy A72", a72.marketingName, "Nombre A72 incorrecto");
        assertEquals("52", a72.rearTeleStandaloneId, "A72 debe tener telefoto 52");
        assertFalse(a72.supportsNativeProVideo, "A72 no soporta Pro Video nativo");

        SamsungModelDatabase.ModelProfile a35 = SamsungModelDatabase.lookup("SM-A356E");
        assertEquals("Samsung Galaxy A35 5G", a35.marketingName, "Nombre A35 incorrecto");
        assertFalse(a35.supportsNativeProVideo, "A35 no soporta Pro Video nativo");

        SamsungModelDatabase.ModelProfile aGeneric = SamsungModelDatabase.lookup("SM-A155M");
        assertEquals("Samsung Galaxy A Series", aGeneric.marketingName, "Fallback Galaxy A incorrecto");
        assertFalse(aGeneric.supportsNativeProVideo, "Galaxy A generico no soporta Pro Video nativo");
    }

    private static void testModelProfileConstructorBackwardsCompatibility() {
        SamsungModelDatabase.ModelProfile legacy = new SamsungModelDatabase.ModelProfile(
                "SM-TEST", "Test Device", "Test", "56", "52", "1", "3", true, true
        );
        assertTrue(legacy.supportsNativeProVideo, "Constructor de 9 args debe usar supportsNativeProVideo = true");
        assertFalse(legacy.hasVariableAperture, "Constructor de 9 args debe usar hasVariableAperture = false");
        assertFalse(legacy.hasFoldableHingeRisk, "Constructor de 9 args debe usar hasFoldableHingeRisk = false");
        assertEquals("", legacy.hardwareNotes, "Constructor de 9 args debe usar hardwareNotes vacio");
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
