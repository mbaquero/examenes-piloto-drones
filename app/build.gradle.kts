plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.examenesdrones.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.examenesdrones.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        // Exported Room schemas are read by the Robolectric migration tests, which only see the
        // variant's merged assets. Debug builds only; release APKs do not include them.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Lets the end-to-end parser test find the study PDF in the project root, if present.
                it.systemProperty("samplePdfDir", rootProject.projectDir.absolutePath)
                it.maxHeapSize = "2g"
            }
        }
    }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

/**
 * The study guide is built into the app: the PDF in the project root is copied into the APK assets
 * as "temario.pdf" and processed on first launch. The build fails if no PDF is present.
 */
val bundledPdfDir = layout.buildDirectory.dir("generated/bundledPdf")
val bundleStudyPdf by tasks.registering(Copy::class) {
    val pdfs = rootProject.projectDir.listFiles { f -> f.isFile && f.extension.equals("pdf", true) }.orEmpty()
    check(pdfs.size == 1) { "Expected exactly one study PDF in ${rootProject.projectDir}, found ${pdfs.size}" }
    from(pdfs.single())
    into(bundledPdfDir)
    rename { "temario.pdf" }
}
android.sourceSets.getByName("main").assets.srcDir(bundledPdfDir)
tasks.named("preBuild") { dependsOn(bundleStudyPdf) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.pdfbox.desktop)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
