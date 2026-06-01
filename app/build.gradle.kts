import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY", "")
val agencySigningKey: String = localProperties.getProperty("AGENCY_SIGNING_KEY", "")

android {
    namespace = "com.meshhood"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meshhood"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "edition"
    productFlavors {
        create("consumer") {
            dimension = "edition"
            buildConfigField("Boolean", "AGENCY_GATEWAY", "false")
            buildConfigField("String", "AGENCY_SIGNING_KEY", "\"\"")
        }
        create("gateway") {
            dimension = "edition"
            applicationIdSuffix = ".gateway"
            versionNameSuffix = "-gateway"
            buildConfigField("Boolean", "AGENCY_GATEWAY", "true")
            buildConfigField("String", "AGENCY_SIGNING_KEY", "\"$agencySigningKey\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // On-device LLM (optional brain). App falls back to the rule-based
    // Coordinator when no model file is present on the device.
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    // Ed25519 signing for the reputation system. Android's Conscrypt lacks an
    // Ed25519 KeyFactory for importing raw peer keys, so we use BouncyCastle's
    // portable low-level primitives instead.
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.protobuf:protobuf-javalite:3.25.1")
}
