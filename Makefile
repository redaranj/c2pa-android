.PHONY: all clean setup library library-gradle publish download-binaries download-native-binaries library-dev tests coverage

# GitHub Release Configuration
GITHUB_ORG := contentauth
C2PA_VERSION := v0.56.2

# Directories
ROOT_DIR := $(shell pwd)
DOWNLOAD_DIR := $(ROOT_DIR)/downloads
LIBRARY_DIR := $(ROOT_DIR)/library

# Android architectures
ANDROID_ARCHS := arm64-v8a armeabi-v7a x86 x86_64
ANDROID_ARM64_TARGET := aarch64-linux-android
ANDROID_ARM_TARGET := armv7-linux-androideabi
ANDROID_X86_TARGET := i686-linux-android
ANDROID_X86_64_TARGET := x86_64-linux-android

# Default target
all: library

# Setup directories
setup:
	@mkdir -p $(DOWNLOAD_DIR)
	@for arch in $(ANDROID_ARCHS); do \
		mkdir -p $(LIBRARY_DIR)/src/main/jniLibs/$$arch; \
	done

# Function to download and extract a pre-built Android library
# Args: 1=architecture name, 2=rust target triple
define download_android_library
	@echo "Downloading Android $(1) library..."
	@curl -sL https://github.com/$(GITHUB_ORG)/c2pa-rs/releases/download/c2pa-$(C2PA_VERSION)/c2pa-$(C2PA_VERSION)-$(2).zip -o $(DOWNLOAD_DIR)/$(1).zip
	@unzip -q -o $(DOWNLOAD_DIR)/$(1).zip -d $(DOWNLOAD_DIR)/$(1)
	@cp $(DOWNLOAD_DIR)/$(1)/lib/libc2pa_c.so $(LIBRARY_DIR)/src/main/jniLibs/$(1)/
	$(if $(3),@cp $(DOWNLOAD_DIR)/$(1)/include/c2pa.h $(LIBRARY_DIR)/src/main/jni/c2pa.h.orig)
endef

# Download pre-built binaries from GitHub releases
download-binaries: download-native-binaries

download-native-binaries: setup
	@echo "Downloading pre-built binaries from $(GITHUB_ORG)/c2pa-rs release c2pa-$(C2PA_VERSION)..."
	
	# Download all Android libraries
	$(call download_android_library,arm64-v8a,aarch64-linux-android,true)
	$(call download_android_library,armeabi-v7a,armv7-linux-androideabi)
	$(call download_android_library,x86,i686-linux-android)
	$(call download_android_library,x86_64,x86_64-linux-android)
	
	# Patch the header file if it was downloaded
	@if [ -f "$(LIBRARY_DIR)/src/main/jni/c2pa.h.orig" ]; then \
		echo "Patching c2pa.h header..."; \
		sed 's/typedef struct C2paSigner C2paSigner;/typedef struct C2paSigner { } C2paSigner;/g' $(LIBRARY_DIR)/src/main/jni/c2pa.h.orig > $(LIBRARY_DIR)/src/main/jni/c2pa.h; \
		rm $(LIBRARY_DIR)/src/main/jni/c2pa.h.orig; \
	fi
	
	@echo "Pre-built binaries downloaded successfully."

# Complete library build: setup, download binaries, and build
library: setup download-native-binaries library-gradle
	@echo "Complete library build finished. AAR available at $(LIBRARY_DIR)/build/outputs/aar/c2pa-release.aar"

# Emulator-only quick build (x86_64) - useful for faster development cycles
library-dev: setup
	@echo "Setting up Android emulator-only build with downloaded x86_64 binary..."
	# Download only x86_64 for faster development
	@mkdir -p $(DOWNLOAD_DIR)
	@mkdir -p $(LIBRARY_DIR)/src/main/jniLibs/x86_64
	@echo "Downloading Android x86_64 library..."
	@curl -sL https://github.com/$(GITHUB_ORG)/c2pa-rs/releases/download/c2pa-$(C2PA_VERSION)/c2pa-$(C2PA_VERSION)-x86_64-linux-android.zip -o $(DOWNLOAD_DIR)/x86_64.zip
	@unzip -q -o $(DOWNLOAD_DIR)/x86_64.zip -d $(DOWNLOAD_DIR)/x86_64
	@cp $(DOWNLOAD_DIR)/x86_64/lib/libc2pa_c.so $(LIBRARY_DIR)/src/main/jniLibs/x86_64/
	@./gradlew :library:assembleDebug
	@echo "Emulator-only library built"

# Run Gradle tasks
library-gradle:
	@echo "Running Gradle build commands..."
	@echo "Using ANDROID_HOME and JAVA_HOME from environment"
	@./gradlew :library:clean :library:assembleRelease
	@echo "Gradle build completed. AAR file available at $(LIBRARY_DIR)/build/outputs/aar/c2pa-release.aar"

# Run tests (instrumented tests on device/emulator)
tests:
	@echo "Running library instrumented tests..."
	@./gradlew :library:connectedDebugAndroidTest
	@echo "Instrumented tests completed."

# Generate code coverage report (requires device/emulator)
coverage:
	@echo "Running instrumented tests with coverage..."
	@./gradlew :library:createDebugCoverageReport :library:jacocoInstrumentedTestReport
	@echo "Coverage report generated at $(LIBRARY_DIR)/build/reports/jacoco/jacocoInstrumentedTestReport/html/index.html"

# Run test app
run-test-app:
	@echo "Installing and running test app..."
	@./gradlew :test-app:app:installDebug
	@adb shell am start -n org.contentauth.c2pa.testapp/.MainActivity

# Publish targets
publish:
	@echo "Publishing library to GitHub packages..."
	@./gradlew :library:publish
	@echo "Library published to GitHub packages at https://maven.pkg.github.com/$(GITHUB_ORG)/c2pa-android"

# Clean target
clean:
	@echo "Cleaning build artifacts..."
	@rm -rf $(DOWNLOAD_DIR)
	@./gradlew clean
	@echo "Clean completed."

# Helper to show available targets
help:
	@echo "Available targets:"
	@echo "  setup                 - Create necessary directories"
	@echo "  download-binaries     - Download pre-built binaries from GitHub releases"
	@echo "  library               - Complete library build (default)"
	@echo "  library-dev           - Download x86_64 library only for emulator"
	@echo "  library-gradle        - Run Gradle build to generate AAR file"
	@echo "  tests                 - Run library instrumented tests (requires device)"
	@echo "  coverage              - Generate instrumented test coverage report (requires device)"
	@echo "  run-test-app          - Install and run the test app"
	@echo "  publish               - Publish library to GitHub packages"
	@echo "  clean                 - Remove build artifacts"
	@echo "  help                  - Show this help message"