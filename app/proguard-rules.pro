# Keep exported Android component entry points declared in AndroidManifest.xml
-keep public class com.s23plus.camera.MainActivity { public *; }
-keep public class com.s23plus.camera.CameraRescueTileService { public *; }

# Keep enums serialized by name in DiagnosticProfileCodec / RescuePreferences
-keep enum com.s23plus.camera.** { *; }
-keepclassmembers enum com.s23plus.camera.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
