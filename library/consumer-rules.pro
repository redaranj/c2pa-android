# Consumer ProGuard rules for C2PA Android library
-keep class org.contentauth.c2pa.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep callback interface (used by JNI)
-keep interface org.contentauth.c2pa.SignCallback { *; }

# Java 17 compatibility
-dontwarn java.lang.invoke.StringConcatFactory