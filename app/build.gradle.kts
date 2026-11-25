import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { stream ->
        keystoreProperties.load(stream)
    }
}

fun resolveSigningValue(envName: String, propName: String): String? {
    val envValue = System.getenv(envName)?.trim()
    if (!envValue.isNullOrEmpty()) {
        return envValue
    }
    val propValue = keystoreProperties.getProperty(propName)?.trim()
    return if (propValue.isNullOrEmpty()) null else propValue
}

val releaseStorePath = resolveSigningValue("ANDROID_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = resolveSigningValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = resolveSigningValue("ANDROID_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = resolveSigningValue("ANDROID_KEY_PASSWORD", "keyPassword")
val releaseStoreFile = releaseStorePath?.let { rootProject.file(it) }
val hasReleaseSigning = !releaseStorePath.isNullOrBlank()
    && !releaseStorePassword.isNullOrBlank()
    && !releaseKeyAlias.isNullOrBlank()
    && !releaseKeyPassword.isNullOrBlank()
    && releaseStoreFile != null
    && releaseStoreFile.exists()

android {
    namespace = "com.s23plus.camera"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.benedetto597.rescuecam"
        minSdk = 30
        targetSdk = 36
        versionCode = 7
        versionName = "2.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ""
        }
    }

    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
