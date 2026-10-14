plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.qbitcore.booklip"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.qbitcore.booklip"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.9.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes.add("META-INF/*.version")
        resources.excludes.add("META-INF/LICENSE*")
    }
    buildToolsVersion = "36.0.0"
}

tasks.matching { it.name.startsWith("process") && it.name.endsWith("Resources") }.configureEach {
    doFirst {
        outputs.files.files.forEach { file ->
            if (file.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
            }
        }
        val buildDir = layout.buildDirectory.get().asFile
        val variantName = name.removePrefix("process").removeSuffix("Resources").replaceFirstChar { it.lowercase() }
        File(buildDir, "intermediates/linked_resources_binary_format/$variantName/$name").mkdirs()
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.browser:browser:1.8.0")
    // MediaSession + media-style notification for text-to-speech playback.
    implementation("androidx.media:media:1.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
