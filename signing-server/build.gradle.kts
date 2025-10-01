import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

group = "org.contentauth.c2pa"
version = "1.0.0"

// Configure source sets for proper IDE recognition
sourceSets {
    main {
        java.srcDirs("src/main/kotlin")
    }
    test {
        java.srcDirs("src/test/kotlin")
    }
}

// Copy necessary source files from library module
tasks.register<Copy>("copyLibrarySources") {
    from("../library/src/main/kotlin/org/contentauth/c2pa") {
        include("C2PA.kt")
        include("C2PAError.kt")
        include("SigningAlgorithm.kt")
        include("SignerInfo.kt")
    }
    into("src/main/kotlin/org/contentauth/c2pa")
    
    doFirst {
        // Clean up old C2PA.kt if it exists
        val oldFile = file("src/main/kotlin/org/contentauth/c2pa/C2PA.kt")
        if (oldFile.exists() && oldFile.length() < 500) { // If it's the minimal version
            oldFile.delete()
        }
    }
}

tasks.compileKotlin {
    dependsOn("copyLibrarySources")
}

dependencies {
    // Dependencies required by C2PA.kt
    implementation("org.json:json:20250517")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")

    // Ktor server
    implementation("io.ktor:ktor-server-core:3.2.3")
    implementation("io.ktor:ktor-server-netty:3.2.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.2.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
    implementation("io.ktor:ktor-server-status-pages:3.2.3")
    implementation("io.ktor:ktor-server-call-logging:3.2.3")
    implementation("io.ktor:ktor-server-default-headers:3.2.3")
    implementation("io.ktor:ktor-server-config-yaml:3.2.3")

    // Configuration
    implementation("com.typesafe:config:1.4.4")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.18")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Crypto and certificates
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.81")

    // Date/Time
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests:2.3.13")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.2.3")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.contentauth.c2pa.signingserver.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

// Task to download macOS C2PA library from iOS releases
tasks.register("downloadNativeLibrary") {
    doLast {
        val libsDir = file("libs")
        libsDir.mkdirs()

        val dylibFile = File(libsDir, "libc2pa_c.dylib")
        if (!dylibFile.exists()) {
            println("Downloading C2PA library for macOS...")
            // For now, we expect it to be copied from iOS project
            // In production, this would download from GitHub releases
            val iosLibPath = file("../../c2pa-ios/Libraries/libc2pa_c.dylib")
            if (iosLibPath.exists()) {
                iosLibPath.copyTo(dylibFile, overwrite = true)
                println("Copied C2PA library from iOS project")
            } else {
                throw GradleException("C2PA library not found. Please copy libc2pa_c.dylib to signing-server/libs/")
            }
        } else {
            println("C2PA library already exists")
        }
    }
}

// Task to compile server JNI (use the existing library JNI)
tasks.register<Exec>("compileServerJNI") {
    dependsOn("downloadNativeLibrary")

    val jniSrcFile = file("../library/src/main/jni/c2pa_jni.c")
    val outputLib = file("libs/libc2pa_server_jni.dylib")

    doFirst {
        if (!jniSrcFile.exists()) {
            throw GradleException("JNI source file not found: $jniSrcFile")
        }
    }

    val javaHome = System.getProperty("java.home")
    commandLine(
        "clang",
        "-shared", "-fPIC",
        "-I$javaHome/include",
        "-I$javaHome/include/darwin",
        "-I../library/src/main/jni",
        "-L./libs", "-lc2pa_c",
        "-o", outputLib.absolutePath,
        jniSrcFile.absolutePath
    )

    doLast {
        println("Compiled server JNI library: $outputLib")
    }
}

tasks.named<JavaExec>("run") {
    // Set working directory to project root
    workingDir = file(".")

    // Set paths for C2PA native libraries
    systemProperty("c2pa.server.lib.path", file("libs/libc2pa_c.dylib").absolutePath)
    systemProperty("c2pa.server.jni.path", file("libs/libc2pa_server_jni.dylib").absolutePath)
}
