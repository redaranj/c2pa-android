.PHONY: all clean setup android android-gradle android-lib publish-android download-binaries download-android-binaries android-dev

# GitHub Release Configuration
GITHUB_ORG := contentauth
C2PA_VERSION := v0.56.2

# Directories
ROOT_DIR := $(shell pwd)
BUILD_DIR := $(ROOT_DIR)/build
OUTPUT_DIR := $(ROOT_DIR)/output
DOWNLOAD_DIR := $(BUILD_DIR)/downloads
TEMPLATE_DIR := $(ROOT_DIR)/template

# Android targets
ANDROID_PACKAGE_NAME := org.contentauth.c2pa
ANDROID_LIB_PATH := $(OUTPUT_DIR)/lib

# Android architectures
ANDROID_ARCHS := arm64-v8a armeabi-v7a x86 x86_64
ANDROID_ARM64_TARGET := aarch64-linux-android
ANDROID_ARM_TARGET := armv7-linux-androideabi
ANDROID_X86_TARGET := i686-linux-android
ANDROID_X86_64_TARGET := x86_64-linux-android

# Default target
all: android

# Setup directories
setup:
	@mkdir -p $(BUILD_DIR)
	@mkdir -p $(OUTPUT_DIR)
	@mkdir -p $(DOWNLOAD_DIR)
	@for arch in $(ANDROID_ARCHS); do \
		mkdir -p $(TEMPLATE_DIR)/c2pa/src/main/jniLibs/$$arch; \
	done
	@mkdir -p $(BUILD_DIR)/patched_headers

# Function to download and extract a pre-built Android library
# Args: 1=architecture name, 2=rust target triple
define download_android_library
	@echo "Downloading Android $(1) library..."
	@curl -sL https://github.com/$(GITHUB_ORG)/c2pa-rs/releases/download/c2pa-$(C2PA_VERSION)/c2pa-$(C2PA_VERSION)-$(2).zip -o $(DOWNLOAD_DIR)/$(1).zip
	@unzip -q -o $(DOWNLOAD_DIR)/$(1).zip -d $(DOWNLOAD_DIR)/$(1)
	@cp $(DOWNLOAD_DIR)/$(1)/lib/libc2pa_c.so $(TEMPLATE_DIR)/c2pa/src/main/jniLibs/$(1)/
	@mkdir -p $(BUILD_DIR)/$(1)/lib && cp $(DOWNLOAD_DIR)/$(1)/lib/libc2pa_c.so $(BUILD_DIR)/$(1)/lib/
	$(if $(3),@cp $(DOWNLOAD_DIR)/$(1)/include/c2pa.h $(BUILD_DIR)/patched_headers/c2pa.h.orig)
endef

# Download pre-built binaries from GitHub releases
download-binaries: download-android-binaries

download-android-binaries: setup
	@echo "Downloading pre-built binaries from $(GITHUB_ORG)/c2pa-rs release c2pa-$(C2PA_VERSION)..."
	
	# Download all Android libraries
	$(call download_android_library,arm64-v8a,aarch64-linux-android,true)
	$(call download_android_library,armeabi-v7a,armv7-linux-androideabi)
	$(call download_android_library,x86,i686-linux-android)
	$(call download_android_library,x86_64,x86_64-linux-android)
	
	# Patch the header file if it was downloaded
	@if [ -f "$(BUILD_DIR)/patched_headers/c2pa.h.orig" ]; then \
		echo "Patching c2pa.h header..."; \
		sed 's/typedef struct C2paSigner C2paSigner;/typedef struct C2paSigner { } C2paSigner;/g' $(BUILD_DIR)/patched_headers/c2pa.h.orig > $(BUILD_DIR)/patched_headers/c2pa.h; \
	fi
	
	@echo "Pre-built binaries downloaded successfully."

# Complete Android build: setup, download binaries, package library, and build AAR
android: setup download-android-binaries android-lib android-gradle
	@echo "Complete Android build finished. AAR available at $(ANDROID_LIB_PATH)/c2pa/build/outputs/aar/"

android-lib:
	@echo "Packaging Android library..."
	@mkdir -p $(OUTPUT_DIR)/lib
	@# Copy all architecture libraries
	@for arch in $(ANDROID_ARCHS); do \
		mkdir -p $(OUTPUT_DIR)/lib/c2pa/src/main/jniLibs/$$arch; \
		mkdir -p $(OUTPUT_DIR)/lib/c2pa/src/main/cpp; \
		if [ -f "$(BUILD_DIR)/$$arch/lib/libc2pa_c.so" ]; then \
			cp $(BUILD_DIR)/$$arch/lib/libc2pa_c.so $(OUTPUT_DIR)/lib/c2pa/src/main/jniLibs/$$arch/; \
		else \
			echo "Warning: Library for $$arch not found at $(BUILD_DIR)/$$arch/lib/libc2pa_c.so"; \
		fi; \
	done
	cp -r $(ROOT_DIR)/template/* $(OUTPUT_DIR)/lib/
	cp $(ROOT_DIR)/src/C2PA.kt $(OUTPUT_DIR)/lib/c2pa/src/main/kotlin/org/contentauth/c2pa/
	cp $(ROOT_DIR)/src/c2pa_jni.c $(OUTPUT_DIR)/lib/c2pa/src/main/jni/
	@echo "Android library packaged at $(OUTPUT_DIR)/lib"

# Android emulator-only quick build (x86_64) - useful for faster development cycles
android-dev: setup
	@echo "Setting up Android emulator-only build with downloaded x86_64 binary..."
	# Download only x86_64 for faster development
	@mkdir -p $(DOWNLOAD_DIR)
	@mkdir -p $(TEMPLATE_DIR)/c2pa/src/main/jniLibs/x86_64
	@echo "Downloading Android x86_64 library..."
	@curl -sL https://github.com/$(GITHUB_ORG)/c2pa-rs/releases/download/c2pa-$(C2PA_VERSION)/c2pa-$(C2PA_VERSION)-x86_64-linux-android.zip -o $(DOWNLOAD_DIR)/x86_64.zip
	@unzip -q -o $(DOWNLOAD_DIR)/x86_64.zip -d $(DOWNLOAD_DIR)/x86_64
	@cp $(DOWNLOAD_DIR)/x86_64/lib/libc2pa_c.so $(TEMPLATE_DIR)/c2pa/src/main/jniLibs/x86_64/
	@mkdir -p $(OUTPUT_DIR)/lib
	@cp -r $(ROOT_DIR)/template/* $(OUTPUT_DIR)/lib/
	@cp $(ROOT_DIR)/src/C2PA.kt $(OUTPUT_DIR)/lib/c2pa/src/main/kotlin/org/contentauth/c2pa/
	@cp $(ROOT_DIR)/src/c2pa_jni.c $(OUTPUT_DIR)/lib/c2pa/src/main/jni/
	@echo "Android emulator-only library built at $(OUTPUT_DIR)/lib"

# Run Gradle tasks
android-gradle: android-lib
	@echo "Running Gradle build commands..."
	@echo "Using ANDROID_HOME and JAVA_HOME from environment"
	@echo "sdk.dir=$$ANDROID_HOME" > $(ANDROID_LIB_PATH)/local.properties
	@if [ -n "$$NDK_VERSION" ]; then echo "ndk.version=$$NDK_VERSION" >> $(ANDROID_LIB_PATH)/local.properties; fi
	@cd $(ANDROID_LIB_PATH) && ./gradlew clean assembleRelease
	@echo "Gradle build completed. AAR file available at $(ANDROID_LIB_PATH)/c2pa/build/outputs/aar/c2pa-release.aar"

# Publish targets
publish:
	@echo "Publishing Android library to GitHub packages..."
	@echo "Using ANDROID_HOME and JAVA_HOME from environment"
	@echo "sdk.dir=$$ANDROID_HOME" > $(ANDROID_LIB_PATH)/local.properties
	@cd $(ANDROID_LIB_PATH) && ./gradlew publish
	@echo "Android library published to GitHub packages at https://maven.pkg.github.com/$(GITHUB_ORG)/c2pa-android"

# Clean target
clean:
	@echo "Cleaning build artifacts..."
	@rm -rf $(BUILD_DIR)
	@rm -rf $(OUTPUT_DIR)
	@echo "Clean completed."

# Helper to show available targets
help:
	@echo "Available targets:"
	@echo "  setup                 - Create necessary directories"
	@echo "  download-binaries     - Download pre-built binaries from GitHub releases"
	@echo "  download-android-binaries - Download pre-built Android binaries"
	@echo "  android               - Complete Android build: setup, download, package, and build AAR"
	@echo "  android-dev           - Download x86_64 library only for emulator (faster development)"
	@echo "  android-lib           - Package Android library"
	@echo "  android-gradle        - Run Gradle build to generate AAR file"
	@echo "  publish               - Publish Android library to GitHub packages"
	@echo "  all                   - Complete Android build (default, same as android)"
	@echo "  clean                 - Remove build artifacts"
	@echo "  help                  - Show this help message"
