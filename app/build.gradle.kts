plugins {
    id("com.android.application")
}

android {
    namespace = "xyz.shapemachine.andsri"
    compileSdk = 36

    defaultConfig {
        applicationId = "xyz.shapemachine.andsri"
        minSdk = 36
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = false
        buildConfig = false
    }

    signingConfigs {
        create("release") {
            storeFile = providers.environmentVariable("ANDSRI_RELEASE_KEYSTORE").orNull?.let(::file)
            storePassword = providers.environmentVariable("ANDSRI_RELEASE_STORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("ANDSRI_RELEASE_KEY_ALIAS").orElse("andsri-release").get()
            keyPassword = providers.environmentVariable("ANDSRI_RELEASE_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
