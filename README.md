# C2PA Android

This project provides Android bindings for the [C2PA](https://c2pa.org/) (Coalition for Content Provenance and Authenticity) libraries. It wraps the C2PA Rust implementation ([c2pa-rs](https://github.com/contentauth/c2pa-rs)) using its C API bindings to provide native Android support via an AAR library.

## Overview

C2PA Android offers:

- Android support via AAR library with Kotlin APIs
- Comprehensive support for C2PA manifest reading, validation, and creation
- Support for callback-based signing and web service signers
- Stream-based operations for efficient memory usage
- Pre-built binaries for fast development

## Repository Structure

- `/library` - Android library module with C2PA Kotlin APIs and JNI bindings
- `/test-app` - Test application with comprehensive test UI
- `/example-app` - Example Android application (placeholder for future camera app)
- `/Makefile` - Build system commands
- `/.github/workflows` - GitHub Actions for CI/CD

## Requirements

### Android

- Android API level 28+ (Android 9.0+)
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17

### Development

- JDK 17 (for Android builds)
- Android SDK (for Android builds)
- Android NDK (any recent version - see note below)
- Make

#### NDK Version

The project will use your default NDK version. If you need to use a specific NDK version, add it to your `local.properties` file:

```properties
ndk.version=29.0.13599879
```

## Installation

### Android (Gradle)

You can add C2PA Android as a Gradle dependency:

```gradle
dependencies {
    implementation "org.contentauth:c2pa:1.0.0"
}
```

Make sure to add the GitHub Packages repository to your project:

```gradle
// In your root build.gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/guardianproject/c2pa-android")
            credentials {
                username = System.getenv("GITHUB_USER") ?: project.findProperty("GITHUB_USER")
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("GITHUB_TOKEN")
            }
        }
    }
}
```

#### Development Workflow for Android

For local development without using a released version:

1. Build the Android library with `make android`
2. Add the resulting library in `output/lib` to your project as a module:

```gradle
// In settings.gradle
include ':app', ':c2pa'
project(':c2pa').projectDir = new File(rootProject.projectDir, 'path/to/output/lib')

// In app/build.gradle
dependencies {
    implementation project(':c2pa')
}
```

## Usage

### Android Examples

#### Reading and Verifying Manifests

```kotlin
import org.contentauth.c2pa.*

// Read a manifest from a file
try {
    val manifest = C2PA.readFile("/path/to/image.jpg")
    println("Manifest: $manifest")
} catch (e: C2PAError) {
    println("Error reading manifest: $e")
}

// Read from a stream
val imageStream = FileStream(File("/path/to/image.jpg"), FileStream.Mode.READ)
try {
    val reader = Reader.fromStream("image/jpeg", imageStream)
    val manifestJson = reader.json()
    println("Manifest JSON: $manifestJson")
    reader.close()
} finally {
    imageStream.close()
}
```

#### Signing Content

```kotlin
// Sign with built-in signer
val signerInfo = SignerInfo(
    algorithm = SigningAlgorithm.ES256,
    certificatePEM = certsPem,
    privateKeyPEM = privateKeyPem,
    tsaURL = "https://timestamp.server.com"
)

val manifest = """{
    "claim_generator": "my_app/1.0",
    "assertions": [
        {"label": "c2pa.actions", "data": {"actions": [{"action": "c2pa.created"}]}}
    ]
}"""

try {
    C2PA.signFile(
        sourcePath = "/path/to/input.jpg",
        destPath = "/path/to/output.jpg",
        manifest = manifest,
        signerInfo = signerInfo
    )
} catch (e: C2PAError) {
    println("Signing failed: $e")
}
```

#### Using Callback Signers

```kotlin
// Create a callback signer for custom signing implementations
val callbackSigner = Signer.withCallback(
    algorithm = SigningAlgorithm.ES256,
    certificateChainPEM = certsPem,
    tsaURL = null
) { data ->
    // Custom signing logic here
    // Return signature bytes in raw R,S format for ECDSA
    myCustomSigningFunction(data)
}

// Use with Builder API
val builder = Builder.fromJson(manifestJson)
val sourceStream = FileStream(File("/path/to/input.jpg"), FileStream.Mode.READ)
val destStream = FileStream(File("/path/to/output.jpg"), FileStream.Mode.WRITE)

try {
    val result = builder.sign("image/jpeg", sourceStream, destStream, callbackSigner)
    println("Signed successfully, size: ${result.size}")
} finally {
    builder.close()
    sourceStream.close()
    destStream.close()
    callbackSigner.close()
}
```

## Building from Source

1. Clone this repository:

   ```bash
   git clone https://github.com/guardianproject/c2pa-android.git
   cd c2pa-android
   ```

2. Set up the required dependencies:
   - Set up JDK 17:

     ```bash
     # macOS with Homebrew:
     brew install openjdk@17
     ```

   - Set up Android SDK
   - Set up environment variables (add to your shell profile):

     ```bash
     export JAVA_HOME=$(/usr/libexec/java_home -v 17)
     export ANDROID_HOME=$HOME/Library/Android/sdk
     ```

3. Build the Android library:

   ```bash
   # Complete build: setup, download binaries, package library, and build AAR
   make android
   
   # For faster Android development (x86_64 emulator only)
   make android-dev
   ```

4. Check built outputs:

   ```bash
   # Android Library
   open output/lib
   
   # Android Example App
   open example
   ```

## Makefile Targets

The project includes a comprehensive Makefile with various targets:

- `setup` - Create necessary directories
- `download-binaries` - Download pre-built binaries from GitHub releases
- `download-android-binaries` - Download pre-built Android binaries
- `android` - Complete Android build: setup, download, package, and build AAR
- `android-dev` - Download x86_64 library only for emulator (faster development)
- `android-lib` - Package Android library
- `android-gradle` - Run Gradle build to generate AAR file
- `publish-android` - Publish Android library to GitHub packages
- `all` - Complete Android build (default, same as android)
- `clean` - Remove build artifacts
- `help` - Show all available targets

## Continuous Integration & Releases

This project uses GitHub Actions for continuous integration and release management:

### Release Process

The release process is automated through a single workflow:

1. **Start a Release**:
   - Trigger the "Release" workflow from the Actions tab
   - Enter the version number (e.g., `v1.0.0`)

2. **Automated Build and Release**:
   - Downloads pre-built C2PA binaries
   - Builds the Android AAR package
   - Creates a GitHub release with the specified version
   - Attaches the Android AAR artifact
   - Publishes documentation for integration

## Example App

For an example of how to use this library, see the example app in `/example` which demonstrates integration with Android apps.

## API Features

### Core Classes

- **C2PA** - Main entry point for static operations (reading files, signing)
- **Reader** - For reading and validating C2PA manifests from streams
- **Builder** - For creating and signing new C2PA manifests
- **Signer** - For signing manifests with various key types and methods
- **Stream** - Base class for stream operations
- **FileStream** - File-based stream implementation
- **MemoryStream** - Memory-based stream implementation

### Signing Options

1. **Direct Signing** - Using private key and certificate PEM strings
2. **Callback Signing** - Custom signing implementations (HSM, cloud KMS, etc.)
3. **Web Service Signing** - Remote signing via HTTP endpoints
4. **Android Keystore** - Hardware-backed key storage (future)

### Important Notes for Callback Signers

When implementing callback signers for ECDSA algorithms (ES256, ES384, ES512), the signature must be returned in raw R,S format, not DER format. The library expects:
- ES256: 64 bytes (32 bytes R + 32 bytes S)
- ES384: 96 bytes (48 bytes R + 48 bytes S) 
- ES512: 132 bytes (66 bytes R + 66 bytes S)

## JNI Implementation

The Android library uses JNI (Java Native Interface) with enhanced memory safety:

- C API headers: `template/c2pa/src/main/jni/c2pa.h`
- JNI implementation: `src/c2pa_jni.c` (thread-safe, proper cleanup)
- Kotlin wrapper: `src/C2PA.kt`

## License

This project is licensed under the Apache License, Version 2.0 and MIT - see the LICENSE-APACHE and LICENSE-MIT files for details.
