import org.gradle.api.publish.maven.MavenPublication
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("jacoco")
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
        debug {
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
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
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Set the base name for the AAR file
base {
    archivesName.set("c2pa")
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.20")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.20")
}

// JaCoCo configuration
jacoco {
    toolVersion = "0.8.10"
}

// Coverage report for instrumented tests only
tasks.register<JacocoReport>("jacocoInstrumentedTestReport") {
    dependsOn("createDebugCoverageReport")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*\$Lambda$*.*",
        "**/*\$inlined$*.*",
        "**/c2pa_jni.*"  // Exclude JNI native code
    )

    val debugTree = fileTree(layout.buildDirectory.dir("intermediates/javac/debug")) {
        exclude(fileFilter)
    }
    val kotlinDebugTree = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude(fileFilter)
    }

    sourceDirectories.setFrom(
        files("src/main/kotlin", "src/main/java")
    )
    classDirectories.setFrom(files(debugTree, kotlinDebugTree))
    
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "outputs/code_coverage/debugAndroidTest/connected/**/coverage.ec"
            )
        }
    )
}

publishing {
    publications {
        create<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
            groupId = "org.contentauth"
            artifactId = "c2pa"
            version = System.getenv("CI_COMMIT_TAG") ?: "1.0.0-SNAPSHOT"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/contentauth/c2pa-android")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// Native library download configuration
val c2paVersion = project.properties["c2paVersion"] as String
val architectures = mapOf(
    "arm64-v8a" to "aarch64-linux-android",
    "armeabi-v7a" to "armv7-linux-androideabi",
    "x86" to "i686-linux-android",
    "x86_64" to "x86_64-linux-android"
)

tasks.register("setupDirectories") {
    doLast {
        val jniLibsDir = file("src/main/jniLibs")
        architectures.keys.forEach { arch ->
            file("$jniLibsDir/$arch").mkdirs()
        }
        file("src/main/jni").mkdirs()
    }
}

tasks.register("downloadNativeLibraries") {
    dependsOn("setupDirectories")
    
    doLast {
        println("Using C2PA version: $c2paVersion")
        val downloadDir = file("${rootDir}/downloads")
        downloadDir.mkdirs()
        
        var headerDownloaded = false
        
        architectures.forEach { (arch, target) ->
            val libDir = file("src/main/jniLibs/$arch")
            val soFile = file("$libDir/libc2pa_c.so")
            
            if (!soFile.exists()) {
                println("Downloading C2PA library for $arch...")
                
                val zipFile = file("$downloadDir/$arch.zip")
                val extractDir = file("$downloadDir/$arch")
                
                // Download the zip file
                val url = "https://github.com/contentauth/c2pa-rs/releases/download/c2pa-$c2paVersion/c2pa-$c2paVersion-$target.zip"
                println("Downloading from: $url")
                ant.invokeMethod("get", mapOf("src" to url, "dest" to zipFile, "skipexisting" to "true"))
                
                // Extract the zip file
                ant.invokeMethod("unzip", mapOf("src" to zipFile, "dest" to extractDir, "overwrite" to "true"))
                
                // Copy the .so file
                file("$extractDir/lib/libc2pa_c.so").copyTo(soFile, overwrite = true)
                
                // Copy header file from first architecture
                if (!headerDownloaded) {
                    val headerFile = file("$extractDir/include/c2pa.h")
                    if (headerFile.exists()) {
                        val destHeader = file("src/main/jni/c2pa.h")
                        headerFile.copyTo(destHeader, overwrite = true)
                        
                        // Patch the header file
                        val content = destHeader.readText()
                        val patchedContent = content.replace(
                            "typedef struct C2paSigner C2paSigner;",
                            "typedef struct C2paSigner { } C2paSigner;"
                        )
                        destHeader.writeText(patchedContent)
                        
                        headerDownloaded = true
                        println("Patched c2pa.h header file")
                    }
                }
            } else {
                println("C2PA library for $arch already exists, skipping download")
            }
        }
    }
}

// Hook into the build process - download libraries before compilation if they don't exist
tasks.named("preBuild") {
    dependsOn("downloadNativeLibraries")
}

// Clean downloaded native libraries
tasks.register("cleanDownloadedLibraries") {
    doLast {
        // Remove downloaded libraries
        architectures.keys.forEach { arch ->
            file("src/main/jniLibs/$arch/libc2pa_c.so").delete()
        }
        
        // Remove header file
        file("src/main/jni/c2pa.h").delete()
        
        // Remove downloads directory
        file("${rootDir}/downloads").deleteRecursively()
        
        println("Cleaned downloaded native libraries")
    }
}

// Hook into clean task
tasks.named("clean") {
    dependsOn("cleanDownloadedLibraries")
}