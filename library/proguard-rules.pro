# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep C2PA classes
-keep class org.contentauth.c2pa.** { *; }

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep callback interface (used by JNI)
-keep interface org.contentauth.c2pa.SignCallback { *; }

# Java 17 compatibility
-dontwarn java.lang.invoke.StringConcatFactory
