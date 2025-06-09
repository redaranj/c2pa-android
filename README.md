# C2PA Android

This project provides Android bindings for the [C2PA](https://c2pa.org/) (Content Authenticity Initiative) libraries. It wraps the C2PA Rust implementation ([c2pa-rs](https://github.com/contentauth/c2pa-rs)) using its C API bindings to provide native Android support via an AAR library.

## Overview

C2PA Android offers:

- Android support via AAR library
- Kotlin APIs for verifying and signing content with C2PA manifests
- Pre-built binaries for fast development

## Repository Structure

- `/template` - Android library template with build configuration
- `/src` - Kotlin wrapper source code and JNI bindings
- `/example` - Example Android application
- `/output` - Build output artifacts
  - `/output/lib` - Built Android AAR library
- `/build` - Temporary build files and downloaded binaries
- `/Makefile` - Build system commands
- `/.github/workflows` - GitHub Actions for CI/CD

## Requirements

### Android

- Android API level 21+ (Android 5.0+)
- Android Studio Arctic Fox (2020.3.1) or newer
- JDK 17

### Development

- JDK 17 (for Android builds)
- Android SDK (for Android builds)
- Make

## Installation

### Android (Gradle)

You can add C2PA Android as a Gradle dependency:

```gradle
dependencies {
    implementation "info.guardianproject:c2pa:1.0.0"
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

### Android Example

```kotlin
import info.guardianproject.c2pa.C2PA

// Initialize C2PA
val c2pa = C2PA()

try {
    // Verify a file
    val isValid = c2pa.verify("/path/to/file.jpg")
    if (isValid) {
        // File has a valid C2PA manifest
        println("Verification successful")
    } else {
        // No valid C2PA manifest found
        println("Verification failed")
    }
    
    // Sign a file
    val success = c2pa.sign(
        "/path/to/input.jpg",
        "/path/to/output.jpg",
        "/path/to/certificate.pem",
        "/path/to/privatekey.pem"
    )
    if (success) {
        println("Signing successful")
    } else {
        println("Signing failed")
    }
} catch (e: C2PA.C2PAException) {
    System.err.println("C2PA error: " + e.message)
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

## JNI Implementation

The Android library uses JNI (Java Native Interface) to connect the Kotlin wrapper to the C2PA C library:

- C API headers are in `template/c2pa/src/main/jni/c2pa.h`
- JNI implementation is in `src/c2pa_jni.c`
- Kotlin wrapper is in `src/C2PA.kt`

## License

This project is licensed under the Apache License, Version 2.0 and MIT - see the LICENSE-APACHE and LICENSE-MIT files for details.