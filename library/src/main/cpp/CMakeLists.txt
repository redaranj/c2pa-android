cmake_minimum_required(VERSION 3.18.1)
project("c2pa_jni")

# Build the JNI wrapper
add_library(c2pa_jni SHARED
    ../jni/c2pa_jni.c)

find_library(log-lib log)

# Link against the c2pa library file directly
target_link_libraries(c2pa_jni
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libc2pa_c.so
    ${log-lib})

# Include directories
target_include_directories(c2pa_jni PRIVATE
    ${CMAKE_SOURCE_DIR}/../jni)

# Enable 16 KB page size support (required for Android 15+ devices)
# NDK r27+ supports this by default, but we set it explicitly for clarity
target_link_options(c2pa_jni PRIVATE
    "-Wl,-z,max-page-size=16384")
