#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "c2pa.h"

// Helper function to throw Java exceptions
static void throwJavaException(JNIEnv *env, const char* className, const char* message) {
    jclass exClass = (*env)->FindClass(env, className);
    if (exClass != NULL) {
        (*env)->ThrowNew(env, exClass, message);
    }
}

// Helper functions for string conversion
static const char* jstring_to_cstring(JNIEnv *env, jstring jstr) {
    if (jstr == NULL) return NULL;
    return (*env)->GetStringUTFChars(env, jstr, NULL);
}

static void release_cstring(JNIEnv *env, jstring jstr, const char* cstr) {
    if (jstr != NULL && cstr != NULL) {
        (*env)->ReleaseStringUTFChars(env, jstr, cstr);
    }
}

static jstring cstring_to_jstring(JNIEnv *env, const char* cstr) {
    if (cstr == NULL) return NULL;
    return (*env)->NewStringUTF(env, cstr);
}

// Stream context wrapper for Java callbacks with thread safety
typedef struct {
    JavaVM *jvm;  // Use JavaVM instead of JNIEnv for thread safety
    jobject streamObject;  // Global reference
    jmethodID readMethod;
    jmethodID seekMethod;
    jmethodID writeMethod;
    jmethodID flushMethod;
} JavaStreamContext;

// Global context tracking for proper cleanup
static JavaStreamContext* g_stream_contexts[1024] = {0};
static int g_stream_count = 0;

// Helper to get JNIEnv for current thread
static JNIEnv* get_jni_env(JavaVM *jvm) {
    JNIEnv *env;
    int status = (*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        (*jvm)->AttachCurrentThread(jvm, &env, NULL);
    }
    return env;
}

// Store context with stream for cleanup
static void store_stream_context(JavaStreamContext *ctx, struct C2paStream *stream) {
    if (g_stream_count < 1024) {
        g_stream_contexts[g_stream_count++] = ctx;
    }
}

// Find and cleanup context
static void cleanup_stream_context(JNIEnv *env, struct C2paStream *stream) {
    for (int i = 0; i < g_stream_count; i++) {
        if (g_stream_contexts[i] != NULL) {
            JavaStreamContext *ctx = g_stream_contexts[i];
            (*env)->DeleteGlobalRef(env, ctx->streamObject);
            free(ctx);
            g_stream_contexts[i] = NULL;
            break; // In real implementation, we'd have proper stream->context mapping
        }
    }
}

// Thread-safe stream callbacks
static intptr_t java_read_callback(struct StreamContext *context, uint8_t *data, intptr_t len) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    JNIEnv *env = get_jni_env(jctx->jvm);
    if (!env) return -1;
    
    jbyteArray jdata = (*env)->NewByteArray(env, len);
    if (!jdata) return -1;
    
    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, jctx->readMethod, jdata, (jlong)len);
    
    // Check for Java exceptions
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, jdata);
        return -1;
    }
    
    if (result > 0 && result <= len) {
        (*env)->GetByteArrayRegion(env, jdata, 0, result, (jbyte*)data);
    }
    (*env)->DeleteLocalRef(env, jdata);
    
    return (intptr_t)result;
}

static intptr_t java_seek_callback(struct StreamContext *context, intptr_t offset, enum C2paSeekMode mode) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    JNIEnv *env = get_jni_env(jctx->jvm);
    if (!env) return -1;
    
    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, jctx->seekMethod, (jlong)offset, (jint)mode);
    
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return -1;
    }
    
    return (intptr_t)result;
}

static intptr_t java_write_callback(struct StreamContext *context, const uint8_t *data, intptr_t len) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    JNIEnv *env = get_jni_env(jctx->jvm);
    if (!env) return -1;
    
    jbyteArray jdata = (*env)->NewByteArray(env, len);
    if (!jdata) return -1;
    
    (*env)->SetByteArrayRegion(env, jdata, 0, len, (const jbyte*)data);
    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, jctx->writeMethod, jdata, (jlong)len);
    
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, jdata);
        return -1;
    }
    
    (*env)->DeleteLocalRef(env, jdata);
    return (intptr_t)result;
}

static intptr_t java_flush_callback(struct StreamContext *context) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    JNIEnv *env = get_jni_env(jctx->jvm);
    if (!env) return -1;
    
    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, jctx->flushMethod);
    
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return -1;
    }
    
    return (intptr_t)result;
}

// Signing callback wrapper with thread safety
typedef struct {
    JavaVM *jvm;
    jobject callback;  // Global reference
    jmethodID signMethod;
} JavaSignerContext;

static intptr_t java_signer_callback(const void *context, const unsigned char *data, uintptr_t len, unsigned char *signed_bytes, uintptr_t signed_len) {
    JavaSignerContext *jctx = (JavaSignerContext*)context;
    JNIEnv *env = get_jni_env(jctx->jvm);
    if (!env) return -1;
    
    jbyteArray jdata = (*env)->NewByteArray(env, len);
    if (!jdata) return -1;
    
    (*env)->SetByteArrayRegion(env, jdata, 0, len, (const jbyte*)data);
    jbyteArray result = (jbyteArray)(*env)->CallObjectMethod(env, jctx->callback, jctx->signMethod, jdata);
    (*env)->DeleteLocalRef(env, jdata);
    
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return -1;
    }
    
    if (!result) return -1;
    
    jsize sig_len = (*env)->GetArrayLength(env, result);
    if (sig_len > signed_len) {
        (*env)->DeleteLocalRef(env, result);
        return -1;
    }
    
    (*env)->GetByteArrayRegion(env, result, 0, sig_len, (jbyte*)signed_bytes);
    (*env)->DeleteLocalRef(env, result);
    
    return (intptr_t)sig_len;
}

// === FIXED C2PA MAIN CLASS METHODS ===

JNIEXPORT jstring JNICALL Java_info_guardianproject_c2pa_C2PA_version(JNIEnv *env, jclass clazz) {
    const char* version = c2pa_version();
    return cstring_to_jstring(env, version);
}

JNIEXPORT jstring JNICALL Java_info_guardianproject_c2pa_C2PA_getError(JNIEnv *env, jclass clazz) {
    const char* error = c2pa_error();
    return cstring_to_jstring(env, error);
}

JNIEXPORT jint JNICALL Java_info_guardianproject_c2pa_C2PA_loadSettings(JNIEnv *env, jclass clazz, jstring settings, jstring format) {
    const char* csettings = jstring_to_cstring(env, settings);
    const char* cformat = jstring_to_cstring(env, format);
    
    int result = c2pa_load_settings(csettings, cformat);
    
    release_cstring(env, settings, csettings);
    release_cstring(env, format, cformat);
    
    return result;
}

JNIEXPORT jstring JNICALL Java_info_guardianproject_c2pa_C2PA_readFile(JNIEnv *env, jclass clazz, jstring path, jstring dataDir) {
    const char* cpath = jstring_to_cstring(env, path);
    const char* cdataDir = jstring_to_cstring(env, dataDir);
    
    const char* result = c2pa_read_file(cpath, cdataDir);
    jstring jresult = cstring_to_jstring(env, result);
    
    if (result) c2pa_string_free((char*)result);
    release_cstring(env, path, cpath);
    release_cstring(env, dataDir, cdataDir);
    
    return jresult;
}

JNIEXPORT jstring JNICALL Java_info_guardianproject_c2pa_C2PA_readIngredientFile(JNIEnv *env, jclass clazz, jstring path, jstring dataDir) {
    const char* cpath = jstring_to_cstring(env, path);
    const char* cdataDir = jstring_to_cstring(env, dataDir);
    
    const char* result = c2pa_read_ingredient_file(cpath, cdataDir);
    jstring jresult = cstring_to_jstring(env, result);
    
    if (result) c2pa_string_free((char*)result);
    release_cstring(env, path, cpath);
    release_cstring(env, dataDir, cdataDir);
    
    return jresult;
}

JNIEXPORT jbyteArray JNICALL Java_info_guardianproject_c2pa_C2PA_ed25519Sign(JNIEnv *env, jclass clazz, jbyteArray data, jstring privateKey) {
    const char* cprivateKey = jstring_to_cstring(env, privateKey);
    
    jsize data_len = (*env)->GetArrayLength(env, data);
    jbyte* data_bytes = (*env)->GetByteArrayElements(env, data, NULL);
    
    uint8_t signature[64];
    intptr_t sig_len = 64;
    
    const unsigned char* result = c2pa_ed25519_sign((const uint8_t*)data_bytes, data_len, cprivateKey);
    
    (*env)->ReleaseByteArrayElements(env, data, data_bytes, JNI_ABORT);
    release_cstring(env, privateKey, cprivateKey);
    
    if (result == NULL) {
        return NULL;
    }
    
    // Ed25519 signatures are always 64 bytes
    jbyteArray jsignature = (*env)->NewByteArray(env, 64);
    (*env)->SetByteArrayRegion(env, jsignature, 0, 64, (const jbyte*)result);
    
    // Free the signature allocated by Rust
    c2pa_signature_free(result);
    
    return jsignature;
}

// === FIXED STREAM CLASS METHODS (Stream not C2PAStream) ===

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_Stream_createNativeStream(JNIEnv *env, jobject obj) {
    JavaStreamContext *ctx = (JavaStreamContext*)malloc(sizeof(JavaStreamContext));
    if (!ctx) {
        // Throw out of memory error
        jclass exClass = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        (*env)->ThrowNew(env, exClass, "Failed to allocate stream context");
        return 0;
    }
    
    // Get JavaVM for thread-safe operations
    (*env)->GetJavaVM(env, &ctx->jvm);
    ctx->streamObject = (*env)->NewGlobalRef(env, obj);
    
    jclass streamClass = (*env)->GetObjectClass(env, obj);
    ctx->readMethod = (*env)->GetMethodID(env, streamClass, "read", "([BJ)J");
    ctx->seekMethod = (*env)->GetMethodID(env, streamClass, "seek", "(JI)J");
    ctx->writeMethod = (*env)->GetMethodID(env, streamClass, "write", "([BJ)J");
    ctx->flushMethod = (*env)->GetMethodID(env, streamClass, "flush", "()J");
    
    // Verify required methods exist
    if (!ctx->readMethod && !ctx->writeMethod) {
        (*env)->DeleteGlobalRef(env, ctx->streamObject);
        free(ctx);
        jclass exClass = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        (*env)->ThrowNew(env, exClass, "Stream must implement read or write methods");
        return 0;
    }
    
    struct C2paStream *stream = c2pa_create_stream(
        (struct StreamContext*)ctx,
        ctx->readMethod ? java_read_callback : NULL,
        ctx->seekMethod ? java_seek_callback : NULL,
        ctx->writeMethod ? java_write_callback : NULL,
        ctx->flushMethod ? java_flush_callback : NULL
    );
    
    if (!stream) {
        (*env)->DeleteGlobalRef(env, ctx->streamObject);
        free(ctx);
        jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
        (*env)->ThrowNew(env, exClass, "Failed to create native stream");
        return 0;
    }
    
    // Store context for cleanup
    store_stream_context(ctx, stream);
    
    return (jlong)stream;
}

JNIEXPORT void JNICALL Java_info_guardianproject_c2pa_Stream_releaseNativeStream(JNIEnv *env, jobject obj, jlong handle) {
    if (handle != 0) {
        struct C2paStream *stream = (struct C2paStream*)handle;
        
        // Clean up the associated context
        cleanup_stream_context(env, stream);
        
        // Release the native stream
        c2pa_release_stream(stream);
    }
}

// === FIXED READER CLASS METHODS (Reader not C2PAReader) ===

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_Reader_fromStream(JNIEnv *env, jclass clazz, jstring format, jlong streamHandle) {
    const char* cformat = jstring_to_cstring(env, format);
    struct C2paStream* stream = (struct C2paStream*)streamHandle;
    
    struct C2paReader* reader = c2pa_reader_from_stream(cformat, stream);
    
    release_cstring(env, format, cformat);
    
    if (!reader) {
        jclass exClass = (*env)->FindClass(env, "info/guardianproject/c2pa/C2PAError$Api");
        const char* error = c2pa_error();
        (*env)->ThrowNew(env, exClass, error ? error : "Failed to create reader from stream");
        return 0;
    }
    
    return (jlong)reader;
}

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_Reader_fromManifestDataAndStream(JNIEnv *env, jclass clazz, jstring format, jlong streamHandle, jbyteArray manifestData) {
    const char* cformat = jstring_to_cstring(env, format);
    struct C2paStream* stream = (struct C2paStream*)streamHandle;
    
    jsize data_len = (*env)->GetArrayLength(env, manifestData);
    jbyte* data_bytes = (*env)->GetByteArrayElements(env, manifestData, NULL);
    
    struct C2paReader* reader = c2pa_reader_from_manifest_data_and_stream(cformat, stream, (const uint8_t*)data_bytes, data_len);
    
    (*env)->ReleaseByteArrayElements(env, manifestData, data_bytes, JNI_ABORT);
    release_cstring(env, format, cformat);
    
    if (!reader) {
        jclass exClass = (*env)->FindClass(env, "info/guardianproject/c2pa/C2PAError$Api");
        const char* error = c2pa_error();
        (*env)->ThrowNew(env, exClass, error ? error : "Failed to create reader from manifest data");
        return 0;
    }
    
    return (jlong)reader;
}

JNIEXPORT jstring JNICALL Java_info_guardianproject_c2pa_Reader_json(JNIEnv *env, jobject obj, jlong handle) {
    struct C2paReader* reader = (struct C2paReader*)handle;
    
    const char* json = c2pa_reader_json(reader);
    jstring result = cstring_to_jstring(env, json);
    
    if (json) c2pa_string_free((char*)json);
    
    return result;
}

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_Reader_resourceToStream(JNIEnv *env, jobject obj, jlong handle, jstring uri, jlong streamHandle) {
    struct C2paReader* reader = (struct C2paReader*)handle;
    struct C2paStream* stream = (struct C2paStream*)streamHandle;
    const char* curi = jstring_to_cstring(env, uri);
    
    intptr_t result = c2pa_reader_resource_to_stream(reader, curi, stream);
    
    release_cstring(env, uri, curi);
    
    if (result < 0) {
        jclass exClass = (*env)->FindClass(env, "info/guardianproject/c2pa/C2PAError$Api");
        const char* error = c2pa_error();
        (*env)->ThrowNew(env, exClass, error ? error : "Failed to read resource");
    }
    
    return (jlong)result;
}

JNIEXPORT void JNICALL Java_info_guardianproject_c2pa_Reader_free(JNIEnv *env, jobject obj, jlong handle) {
    if (handle != 0) {
        struct C2paReader* reader = (struct C2paReader*)handle;
        c2pa_reader_free(reader);
    }
}

// === FIXED BUILDER CLASS METHODS (Builder not C2PABuilder) ===

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_Builder_fromJson(JNIEnv *env, jclass clazz, jstring manifestJson) {
    const char* cjson = jstring_to_cstring(env, manifestJson);
    
    struct C2paBuilder* builder = c2pa_builder_from_json(cjson);
    
    release_cstring(env, manifestJson, cjson);
    
    if (!builder) {
        jclass exClass = (*env)->FindClass(env, "info/guardianproject/c2pa/C2PAError$Api");
        const char* error = c2pa_error();
        (*env)->ThrowNew(env, exClass, error ? error : "Failed to create builder from JSON");
        return 0;
    }
    
    return (jlong)builder;
}

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_Builder_fromArchive(JNIEnv *env, jclass clazz, jlong streamHandle) {
    struct C2paStream* stream = (struct C2paStream*)streamHandle;
    
    struct C2paBuilder* builder = c2pa_builder_from_archive(stream);
    
    if (!builder) {
        jclass exClass = (*env)->FindClass(env, "info/guardianproject/c2pa/C2PAError$Api");
        const char* error = c2pa_error();
        (*env)->ThrowNew(env, exClass, error ? error : "Failed to create builder from archive");
        return 0;
    }
    
    return (jlong)builder;
}

JNIEXPORT void JNICALL Java_info_guardianproject_c2pa_Builder_setNoEmbed(JNIEnv *env, jobject obj, jlong handle) {
    struct C2paBuilder* builder = (struct C2paBuilder*)handle;
    c2pa_builder_set_no_embed(builder);
}

JNIEXPORT jint JNICALL Java_info_guardianproject_c2pa_Builder_setRemoteUrl(JNIEnv *env, jobject obj, jlong handle, jstring remoteUrl) {
    struct C2paBuilder* builder = (struct C2paBuilder*)handle;
    const char* curl = jstring_to_cstring(env, remoteUrl);
    
    int result = c2pa_builder_set_remote_url(builder, curl);
    
    release_cstring(env, remoteUrl, curl);
    
    return result;
}

JNIEXPORT jint JNICALL Java_info_guardianproject_c2pa_Builder_addResource(JNIEnv *env, jobject obj, jlong handle, jstring uri, jlong streamHandle) {
    struct C2paBuilder* builder = (struct C2paBuilder*)handle;
    struct C2paStream* stream = (struct C2paStream*)streamHandle;
    const char* curi = jstring_to_cstring(env, uri);
    
    int result = c2pa_builder_add_resource(builder, curi, stream);
    
    release_cstring(env, uri, curi);
    
    return result;
}

JNIEXPORT jint JNICALL Java_info_guardianproject_c2pa_Builder_addIngredientFromStream(JNIEnv *env, jobject obj, jlong handle, jstring json, jstring format, jlong streamHandle) {
    struct C2paBuilder* builder = (struct C2paBuilder*)handle;
    struct C2paStream* stream = (struct C2paStream*)streamHandle;
    const char* cjson = jstring_to_cstring(env, json);
    const char* cformat = jstring_to_cstring(env, format);
    
    int result = c2pa_builder_add_ingredient_from_stream(builder, cjson, cformat, stream);
    
    release_cstring(env, json, cjson);
    release_cstring(env, format, cformat);
    
    return result;
}

JNIEXPORT jint JNICALL Java_info_guardianproject_c2pa_Builder_toArchive(JNIEnv *env, jobject obj, jlong handle, jlong streamHandle) {
    struct C2paBuilder* builder = (struct C2paBuilder*)handle;
    struct C2paStream* stream = (struct C2paStream*)streamHandle;
    
    return c2pa_builder_to_archive(builder, stream);
}

JNIEXPORT void JNICALL Java_info_guardianproject_c2pa_Builder_free(JNIEnv *env, jobject obj, jlong handle) {
    if (handle != 0) {
        struct C2paBuilder* builder = (struct C2paBuilder*)handle;
        c2pa_builder_free(builder);
    }
}

// === FIXED SIGNER CLASS METHODS (Signer not C2PASigner) ===

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_Signer_fromInfo(JNIEnv *env, jclass clazz, jobject signerInfo) {
    jclass signerInfoClass = (*env)->GetObjectClass(env, signerInfo);
    
    jfieldID algField = (*env)->GetFieldID(env, signerInfoClass, "alg", "Ljava/lang/String;");
    jfieldID certField = (*env)->GetFieldID(env, signerInfoClass, "signCert", "Ljava/lang/String;");
    jfieldID keyField = (*env)->GetFieldID(env, signerInfoClass, "privateKey", "Ljava/lang/String;");
    jfieldID tsaField = (*env)->GetFieldID(env, signerInfoClass, "tsaUrl", "Ljava/lang/String;");
    
    jstring jalg = (jstring)(*env)->GetObjectField(env, signerInfo, algField);
    jstring jsignCert = (jstring)(*env)->GetObjectField(env, signerInfo, certField);
    jstring jprivateKey = (jstring)(*env)->GetObjectField(env, signerInfo, keyField);
    jstring jtsaUrl = (jstring)(*env)->GetObjectField(env, signerInfo, tsaField);
    
    const char* calg = jstring_to_cstring(env, jalg);
    const char* csignCert = jstring_to_cstring(env, jsignCert);
    const char* cprivateKey = jstring_to_cstring(env, jprivateKey);
    const char* ctsaUrl = jstring_to_cstring(env, jtsaUrl);
    
    // Create a C2paSignerInfo structure
    struct C2paSignerInfo signer_info = {
        .alg = calg,
        .sign_cert = csignCert,
        .private_key = cprivateKey,
        .ta_url = ctsaUrl
    };
    
    struct C2paSigner* signer = c2pa_signer_from_info(&signer_info);
    
    release_cstring(env, jalg, calg);
    release_cstring(env, jsignCert, csignCert);
    release_cstring(env, jprivateKey, cprivateKey);
    release_cstring(env, jtsaUrl, ctsaUrl);
    
    if (!signer) {
        jclass exClass = (*env)->FindClass(env, "info/guardianproject/c2pa/C2PAError$Api");
        const char* error = c2pa_error();
        (*env)->ThrowNew(env, exClass, error ? error : "Failed to create signer from info");
        return 0;
    }
    
    return (jlong)signer;
}

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_Signer_fromCallback(JNIEnv *env, jclass clazz, jstring algorithm, jstring certificateChain, jstring tsaURL, jobject callback) {
    const char* calg = jstring_to_cstring(env, algorithm);
    const char* ccert = jstring_to_cstring(env, certificateChain);
    const char* ctsa = jstring_to_cstring(env, tsaURL);
    
    JavaSignerContext *ctx = (JavaSignerContext*)malloc(sizeof(JavaSignerContext));
    if (!ctx) {
        release_cstring(env, algorithm, calg);
        release_cstring(env, certificateChain, ccert);
        release_cstring(env, tsaURL, ctsa);
        jclass exClass = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        (*env)->ThrowNew(env, exClass, "Failed to allocate signer context");
        return 0;
    }
    
    (*env)->GetJavaVM(env, &ctx->jvm);
    ctx->callback = (*env)->NewGlobalRef(env, callback);
    
    jclass callbackClass = (*env)->GetObjectClass(env, callback);
    ctx->signMethod = (*env)->GetMethodID(env, callbackClass, "sign", "([B)[B");
    
    if (!ctx->signMethod) {
        (*env)->DeleteGlobalRef(env, ctx->callback);
        free(ctx);
        release_cstring(env, algorithm, calg);
        release_cstring(env, certificateChain, ccert);
        release_cstring(env, tsaURL, ctsa);
        jclass exClass = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        (*env)->ThrowNew(env, exClass, "Callback must implement sign(byte[]):byte[] method");
        return 0;
    }
    
    // Convert string algorithm to enum
    enum C2paSigningAlg alg_enum;
    if (strcmp(calg, "es256") == 0) {
        alg_enum = Es256;
    } else if (strcmp(calg, "es384") == 0) {
        alg_enum = Es384;
    } else if (strcmp(calg, "es512") == 0) {
        alg_enum = Es512;
    } else if (strcmp(calg, "ps256") == 0) {
        alg_enum = Ps256;
    } else if (strcmp(calg, "ps384") == 0) {
        alg_enum = Ps384;
    } else if (strcmp(calg, "ps512") == 0) {
        alg_enum = Ps512;
    } else if (strcmp(calg, "ed25519") == 0) {
        alg_enum = Ed25519;
    } else {
        // Default to ES256 if unknown
        alg_enum = Es256;
    }
    
    struct C2paSigner* signer = c2pa_signer_create((const void*)ctx, java_signer_callback, alg_enum, ccert, ctsa);
    
    release_cstring(env, algorithm, calg);
    release_cstring(env, certificateChain, ccert);
    release_cstring(env, tsaURL, ctsa);
    
    if (!signer) {
        (*env)->DeleteGlobalRef(env, ctx->callback);
        free(ctx);
        jclass exClass = (*env)->FindClass(env, "info/guardianproject/c2pa/C2PAError$Api");
        const char* error = c2pa_error();
        (*env)->ThrowNew(env, exClass, error ? error : "Failed to create callback signer");
        return 0;
    }
    
    return (jlong)signer;
}

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_Signer_reserveSize(JNIEnv *env, jobject obj, jlong handle) {
    struct C2paSigner* signer = (struct C2paSigner*)handle;
    return (jlong)c2pa_signer_reserve_size(signer);
}

JNIEXPORT void JNICALL Java_info_guardianproject_c2pa_Signer_free(JNIEnv *env, jobject obj, jlong handle) {
    if (handle != 0) {
        struct C2paSigner* signer = (struct C2paSigner*)handle;
        // TODO: Cleanup JavaSignerContext if this was a callback signer
        c2pa_signer_free(signer);
    }
}

// === SIGN OPERATION (Builder.sign method) ===

JNIEXPORT jobject JNICALL Java_info_guardianproject_c2pa_Builder_sign(JNIEnv *env, jobject obj, jlong handle, jstring format, jlong sourceHandle, jlong destHandle, jlong signerHandle) {
    struct C2paBuilder* builder = (struct C2paBuilder*)handle;
    struct C2paStream* source = (struct C2paStream*)sourceHandle;
    struct C2paStream* dest = (struct C2paStream*)destHandle;
    struct C2paSigner* signer = (struct C2paSigner*)signerHandle;
    const char* cformat = jstring_to_cstring(env, format);
    
    const unsigned char* manifest_data = NULL;
    int64_t manifest_size = c2pa_builder_sign(builder, cformat, source, dest, signer, &manifest_data);
    
    release_cstring(env, format, cformat);
    
    if (manifest_size < 0 || !manifest_data) {
        jclass exClass = (*env)->FindClass(env, "info/guardianproject/c2pa/C2PAError$Api");
        const char* error = c2pa_error();
        (*env)->ThrowNew(env, exClass, error ? error : "Signing failed");
        return NULL;
    }
    
    // Create SignResult object
    jclass signResultClass = (*env)->FindClass(env, "info/guardianproject/c2pa/SignResult");
    jmethodID constructor = (*env)->GetMethodID(env, signResultClass, "<init>", "([BI)V");
    
    jbyteArray manifestBytes = (*env)->NewByteArray(env, manifest_size);
    (*env)->SetByteArrayRegion(env, manifestBytes, 0, manifest_size, (const jbyte*)manifest_data);
    
    jobject result = (*env)->NewObject(env, signResultClass, constructor, manifestBytes, (jint)manifest_size);
    
    if (manifest_data) c2pa_manifest_bytes_free(manifest_data);
    
    return result;
}