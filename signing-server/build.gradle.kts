import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

group = "org.contentauth.c2pa"
version = "1.0.0"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:2.3.8")
    implementation("io.ktor:ktor-server-netty:2.3.8")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.8")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")
    implementation("io.ktor:ktor-server-cors:2.3.8")
    implementation("io.ktor:ktor-server-status-pages:2.3.8")
    implementation("io.ktor:ktor-server-call-logging:2.3.8")
    implementation("io.ktor:ktor-server-default-headers:2.3.8")
    implementation("io.ktor:ktor-server-config-yaml:2.3.8")
    
    // Configuration
    implementation("com.typesafe:config:1.4.3")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    // Crypto and certificates
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.77")
    
    // Date/Time
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests:2.3.8")
    testImplementation("io.ktor:ktor-client-content-negotiation:2.3.8")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.contentauth.c2pa.signingserver.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

tasks.named<JavaExec>("run") {
    // Set working directory to project root
    workingDir = file(".")
    
    // Add JNI library path
    systemProperty("java.library.path", file("../library/src/main/jniLibs/x86_64").absolutePath)
}