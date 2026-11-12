plugins {
    id("com.android.application")
}

android {
    namespace = "com.s23plus.camera"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.s23plus.camera"
        minSdk = 30
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
