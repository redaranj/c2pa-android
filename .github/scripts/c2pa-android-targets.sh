#!/usr/bin/env bash
# Sourced, never executed. The four Android targets whose c2pa-c-ffi archives
# the downloadNativeLibraries Gradle task consumes, one per line.
#
# Used by check-c2pa-assets.sh (release preflight) and
# build-c2pa-archives.sh (self-built archives for the main tracker). The list
# must match the values of the `architectures` map in
# library/build.gradle.kts; the two must always change together.
# shellcheck disable=SC2034
C2PA_ANDROID_TARGETS="
aarch64-linux-android
armv7-linux-androideabi
i686-linux-android
x86_64-linux-android
"
