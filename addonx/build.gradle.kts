import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

val brand =
    Properties().apply {
        rootProject.file("brand.properties").inputStream().use { load(it) }
    }

android {
    namespace = "io.github.tukiza7_debug.vibecast.addonx"
    compileSdk = 36

    defaultConfig {
        applicationId = brand.getProperty("addonx.packageId")
        minSdk = 24
        targetSdk = 36
        versionCode = providers.gradleProperty("addonx.versionCode").orElse("1000000").get().toInt()
        versionName = providers.gradleProperty("addonx.versionName").orElse("1.0.0").get()

        resValue("string", "app_name", brand.getProperty("addonx.appName"))
        resValue("string", "repo_owner", brand.getProperty("owner"))

        manifestPlaceholders["vibecastPermission"] = brand.getProperty("vibecast.extensionPermission")
    }

    signingConfigs {
        create("release") {
            val keystoreB64 = System.getenv("RELEASE_KEYSTORE_B64")
            val storePass = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            val alias = System.getenv("RELEASE_KEY_ALIAS")
            val keyPass = System.getenv("RELEASE_KEY_PASSWORD")
            if (keystoreB64 != null && storePass != null && alias != null && keyPass != null) {
                val keystoreDir = layout.buildDirectory.dir("signing").get().asFile
                keystoreDir.mkdirs()
                val keystoreFile = File(keystoreDir, "release.keystore")
                keystoreFile.writeBytes(Base64.getMimeDecoder().decode(keystoreB64))
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    afterEvaluate {
        val asksForRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
        if (asksForRelease && signingConfigs.getByName("release").storeFile == null) {
            throw GradleException(
                "Release build requested but signing secrets are missing. " +
                    "Set RELEASE_KEYSTORE_B64, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD.",
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abi = if (output.filters.isEmpty()) "universal" else output.filters.first().identifier
            val appName = brand.getProperty("addonx.appName")
            output.outputFileName = "$appName-$versionName-$abi.apk"
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

    packaging {
        resources.excludes += setOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
    }

    lint {
        abortOnError = true
        // The verification gate runs `lint` explicitly; lintVital duplicates that
        // work inside assembleRelease and doubles the memory needed for R8.
        checkReleaseBuilds = false
        disable += setOf("MissingTranslation", "GoogleAppIndexingWarning", "UnknownNullness")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":adblock"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

detekt {
    config.setFrom(rootProject.file("detekt.yml"))
}
