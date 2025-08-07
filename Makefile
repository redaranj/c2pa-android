.PHONY: all clean setup library publish download-binaries tests coverage help run-test-app run-example-app

# Default target
all: library

# Setup directories using Gradle task
setup:
	@echo "Setting up directories..."
	@./gradlew :library:setupDirectories

# Download pre-built binaries from GitHub releases using Gradle task
download-binaries:
	@echo "Downloading pre-built binaries..."
	@./gradlew :library:downloadNativeLibraries

# Complete library build: setup, download binaries, and build
library: setup download-binaries
	@echo "Building library..."
	@./gradlew :library:clean :library:assembleRelease
	@echo "Library build completed. AAR available at library/build/outputs/aar/c2pa-release.aar"

# Run tests (instrumented tests on device/emulator)
tests:
	@echo "Running library instrumented tests..."
	@./gradlew :library:connectedDebugAndroidTest

# Generate code coverage report (requires device/emulator)
coverage:
	@echo "Running instrumented tests with coverage..."
	@./gradlew :library:createDebugCoverageReport :library:jacocoInstrumentedTestReport
	@echo "Coverage report generated at library/build/reports/jacoco/jacocoInstrumentedTestReport/html/index.html"

# Run test app
run-test-app:
	@echo "Installing and running test app..."
	@./gradlew :test-app:app:installDebug
	@adb shell am start -n org.contentauth.c2pa.testapp/org.contentauth.c2pa.testapp.MainActivity

# Run example app
run-example-app:
	@echo "Installing and running example app..."
	@./gradlew :example-app:app:installDebug
	@adb shell am start -n org.contentauth.c2pa.exampleapp/org.contentauth.c2pa.example.MainActivity

# Publish library to GitHub packages
publish:
	@echo "Publishing library to GitHub packages..."
	@./gradlew :library:publish

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	@./gradlew clean

# Helper to show available targets
help:
	@echo "Available targets:"
	@echo "  setup                 - Create necessary directories"
	@echo "  download-binaries     - Download pre-built binaries from GitHub releases"
	@echo "  library               - Complete library build (default)"
	@echo "  tests                 - Run library instrumented tests (requires device)"
	@echo "  coverage              - Generate instrumented test coverage report (requires device)"
	@echo "  run-test-app          - Install and run the test app"
	@echo "  run-example-app       - Install and run the example app"
	@echo "  publish               - Publish library to GitHub packages"
	@echo "  clean                 - Remove build artifacts"
	@echo "  help                  - Show this help message"