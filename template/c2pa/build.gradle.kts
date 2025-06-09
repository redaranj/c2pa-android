import org.gradle.api.publish.maven.MavenPublication
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

android {
    namespace = "org.contentauth.c2pa"
    compileSdk = 35

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // CMake configuration
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
            }
        }

        // Specify ABIs to use prebuilt .so files
        ndk {
            abiFilters.add("x86_64")
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
            abiFilters.add("x86")
        }
    }

    // NDK version - can be overridden in local.properties with ndk.version=XX.X.XXXXXXX
    // If not specified, the default NDK bundled with Android Studio will be used
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    val localNdkVersion = localProperties.getProperty("ndk.version")
    if (localNdkVersion != null) {
        println("Using NDK version from local.properties: $localNdkVersion")
        ndkVersion = localNdkVersion
    } else {
        println("No NDK version specified in local.properties, using default")
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

    // CMake configuration for JNI code
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    // Make sure to include JNI libs
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}

publishing {
  publications {
    create<MavenPublication>("release") {
      artifact("$buildDir/outputs/aar/${project.name}-release.aar")
      groupId = "org.contentauth"
      artifactId = "c2pa"
      version = System.getenv("CI_COMMIT_TAG") as String?
    }
  }
  repositories {
    maven {
      name = "GitHubPackages"
      url  = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_ORG") ?: "contentauth"}/c2pa-android")
      credentials {
        username = System.getenv("GITHUB_ACTOR")
        password = System.getenv("GITHUB_TOKEN")
      }
    }
  }
}
