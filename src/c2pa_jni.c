#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "c2pa.h"

// Helper function to convert jstring to C string
static const char* jstring_to_cstring(JNIEnv *env, jstring jstr) {
    if (jstr == NULL) return NULL;
    return (*env)->GetStringUTFChars(env, jstr, NULL);
}

// Helper function to release C string from jstring
static void release_cstring(JNIEnv *env, jstring jstr, const char* cstr) {
    if (jstr != NULL && cstr != NULL) {
        (*env)->ReleaseStringUTFChars(env, jstr, cstr);
    }
}

// Helper function to convert C string to jstring
static jstring cstring_to_jstring(JNIEnv *env, const char* cstr) {
    if (cstr == NULL) return NULL;
    return (*env)->NewStringUTF(env, cstr);
}

// Stream context wrapper for Java callbacks
typedef struct {
    JNIEnv *env;
    jobject streamObject;
    jmethodID readMethod;
    jmethodID seekMethod;
    jmethodID writeMethod;
    jmethodID flushMethod;
} JavaStreamContext;

// Stream callbacks
static intptr_t java_read_callback(struct StreamContext *context, uint8_t *data, intptr_t len) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    JNIEnv *env = jctx->env;
    
    jbyteArray jdata = (*env)->NewByteArray(env, len);
    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, jctx->readMethod, jdata, (jlong)len);
    
    if (result > 0) {
        (*env)->GetByteArrayRegion(env, jdata, 0, result, (jbyte*)data);
    }
    (*env)->DeleteLocalRef(env, jdata);
    
    return (intptr_t)result;
}

static intptr_t java_seek_callback(struct StreamContext *context, intptr_t offset, enum C2paSeekMode mode) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    return (intptr_t)(*jctx->env)->CallLongMethod(jctx->env, jctx->streamObject, jctx->seekMethod, (jlong)offset, (jint)mode);
}

static intptr_t java_write_callback(struct StreamContext *context, const uint8_t *data, intptr_t len) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    JNIEnv *env = jctx->env;
    
    jbyteArray jdata = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, jdata, 0, len, (const jbyte*)data);
    jlong result = (*env)->CallLongMethod(env, jctx->streamObject, jctx->writeMethod, jdata, (jlong)len);
    (*env)->DeleteLocalRef(env, jdata);
    
    return (intptr_t)result;
}

static intptr_t java_flush_callback(struct StreamContext *context) {
    JavaStreamContext *jctx = (JavaStreamContext*)context;
    return (intptr_t)(*jctx->env)->CallLongMethod(jctx->env, jctx->streamObject, jctx->flushMethod);
}

// Native methods implementation

JNIEXPORT jstring JNICALL Java_info_guardianproject_c2pa_C2PA_version(JNIEnv *env, jclass clazz) {
    char *version = c2pa_version();
    jstring result = cstring_to_jstring(env, version);
    c2pa_string_free(version);
    return result;
}

JNIEXPORT jstring JNICALL Java_info_guardianproject_c2pa_C2PA_getError(JNIEnv *env, jclass clazz) {
    char *error = c2pa_error();
    jstring result = cstring_to_jstring(env, error);
    c2pa_string_free(error);
    return result;
}

JNIEXPORT jint JNICALL Java_info_guardianproject_c2pa_C2PA_loadSettings(JNIEnv *env, jclass clazz, jstring settings, jstring format) {
    const char *csettings = jstring_to_cstring(env, settings);
    const char *cformat = jstring_to_cstring(env, format);
    
    int result = c2pa_load_settings(csettings, cformat);
    
    release_cstring(env, settings, csettings);
    release_cstring(env, format, cformat);
    
    return result;
}

JNIEXPORT jstring JNICALL Java_info_guardianproject_c2pa_C2PA_readFile(JNIEnv *env, jclass clazz, jstring path, jstring dataDir) {
    const char *cpath = jstring_to_cstring(env, path);
    const char *cdataDir = jstring_to_cstring(env, dataDir);
    
    char *result = c2pa_read_file(cpath, cdataDir);
    jstring jresult = cstring_to_jstring(env, result);
    
    c2pa_string_free(result);
    release_cstring(env, path, cpath);
    release_cstring(env, dataDir, cdataDir);
    
    return jresult;
}

JNIEXPORT jstring JNICALL Java_info_guardianproject_c2pa_C2PA_readIngredientFile(JNIEnv *env, jclass clazz, jstring path, jstring dataDir) {
    const char *cpath = jstring_to_cstring(env, path);
    const char *cdataDir = jstring_to_cstring(env, dataDir);
    
    char *result = c2pa_read_ingredient_file(cpath, cdataDir);
    jstring jresult = cstring_to_jstring(env, result);
    
    c2pa_string_free(result);
    release_cstring(env, path, cpath);
    release_cstring(env, dataDir, cdataDir);
    
    return jresult;
}

JNIEXPORT jstring JNICALL Java_info_guardianproject_c2pa_C2PA_signFile(JNIEnv *env, jclass clazz, jstring sourcePath, jstring destPath, jstring manifest, jobject signerInfo, jstring dataDir) {
    const char *csourcePath = jstring_to_cstring(env, sourcePath);
    const char *cdestPath = jstring_to_cstring(env, destPath);
    const char *cmanifest = jstring_to_cstring(env, manifest);
    const char *cdataDir = jstring_to_cstring(env, dataDir);
    
    // Get SignerInfo fields
    jclass signerInfoClass = (*env)->GetObjectClass(env, signerInfo);
    jfieldID algField = (*env)->GetFieldID(env, signerInfoClass, "alg", "Ljava/lang/String;");
    jfieldID signCertField = (*env)->GetFieldID(env, signerInfoClass, "signCert", "Ljava/lang/String;");
    jfieldID privateKeyField = (*env)->GetFieldID(env, signerInfoClass, "privateKey", "Ljava/lang/String;");
    jfieldID taUrlField = (*env)->GetFieldID(env, signerInfoClass, "taUrl", "Ljava/lang/String;");
    
    jstring jalg = (*env)->GetObjectField(env, signerInfo, algField);
    jstring jsignCert = (*env)->GetObjectField(env, signerInfo, signCertField);
    jstring jprivateKey = (*env)->GetObjectField(env, signerInfo, privateKeyField);
    jstring jtaUrl = (*env)->GetObjectField(env, signerInfo, taUrlField);
    
    const char *calg = jstring_to_cstring(env, jalg);
    const char *csignCert = jstring_to_cstring(env, jsignCert);
    const char *cprivateKey = jstring_to_cstring(env, jprivateKey);
    const char *ctaUrl = jstring_to_cstring(env, jtaUrl);
    
    struct C2paSignerInfo cSignerInfo = {
        .alg = calg,
        .sign_cert = csignCert,
        .private_key = cprivateKey,
        .ta_url = ctaUrl
    };
    
    char *result = c2pa_sign_file(csourcePath, cdestPath, cmanifest, &cSignerInfo, cdataDir);
    jstring jresult = cstring_to_jstring(env, result);
    
    c2pa_string_free(result);
    release_cstring(env, sourcePath, csourcePath);
    release_cstring(env, destPath, cdestPath);
    release_cstring(env, manifest, cmanifest);
    release_cstring(env, dataDir, cdataDir);
    release_cstring(env, jalg, calg);
    release_cstring(env, jsignCert, csignCert);
    release_cstring(env, jprivateKey, cprivateKey);
    release_cstring(env, jtaUrl, ctaUrl);
    
    return jresult;
}

// Stream native methods
JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_C2PAStream_createNativeStream(JNIEnv *env, jobject obj) {
    JavaStreamContext *ctx = (JavaStreamContext*)malloc(sizeof(JavaStreamContext));
    ctx->env = env;
    ctx->streamObject = (*env)->NewGlobalRef(env, obj);
    
    jclass streamClass = (*env)->GetObjectClass(env, obj);
    ctx->readMethod = (*env)->GetMethodID(env, streamClass, "read", "([BJ)J");
    ctx->seekMethod = (*env)->GetMethodID(env, streamClass, "seek", "(JI)J");
    ctx->writeMethod = (*env)->GetMethodID(env, streamClass, "write", "([BJ)J");
    ctx->flushMethod = (*env)->GetMethodID(env, streamClass, "flush", "()J");
    
    struct C2paStream *stream = c2pa_create_stream(
        (struct StreamContext*)ctx,
        java_read_callback,
        java_seek_callback,
        java_write_callback,
        java_flush_callback
    );
    
    return (jlong)stream;
}

JNIEXPORT void JNICALL Java_info_guardianproject_c2pa_C2PAStream_releaseNativeStream(JNIEnv *env, jobject obj, jlong streamPtr) {
    if (streamPtr != 0) {
        struct C2paStream *stream = (struct C2paStream*)streamPtr;
        // Free the Java context
        JavaStreamContext *ctx = (JavaStreamContext*)stream->context;
        (*env)->DeleteGlobalRef(env, ctx->streamObject);
        free(ctx);
        // Release the stream
        c2pa_release_stream(stream);
    }
}

// Reader native methods
JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_C2PAReader_fromStream(JNIEnv *env, jclass clazz, jstring format, jlong streamPtr) {
    const char *cformat = jstring_to_cstring(env, format);
    struct C2paStream *stream = (struct C2paStream*)streamPtr;
    
    struct C2paReader *reader = c2pa_reader_from_stream(cformat, stream);
    
    release_cstring(env, format, cformat);
    
    return (jlong)reader;
}

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_C2PAReader_fromManifestDataAndStream(JNIEnv *env, jclass clazz, jstring format, jlong streamPtr, jbyteArray manifestData) {
    const char *cformat = jstring_to_cstring(env, format);
    struct C2paStream *stream = (struct C2paStream*)streamPtr;
    
    jsize dataSize = (*env)->GetArrayLength(env, manifestData);
    jbyte *data = (*env)->GetByteArrayElements(env, manifestData, NULL);
    
    struct C2paReader *reader = c2pa_reader_from_manifest_data_and_stream(
        cformat, stream, (const unsigned char*)data, dataSize
    );
    
    (*env)->ReleaseByteArrayElements(env, manifestData, data, JNI_ABORT);
    release_cstring(env, format, cformat);
    
    return (jlong)reader;
}

JNIEXPORT void JNICALL Java_info_guardianproject_c2pa_C2PAReader_free(JNIEnv *env, jobject obj, jlong readerPtr) {
    if (readerPtr != 0) {
        c2pa_reader_free((struct C2paReader*)readerPtr);
    }
}

JNIEXPORT jstring JNICALL Java_info_guardianproject_c2pa_C2PAReader_toJson(JNIEnv *env, jobject obj, jlong readerPtr) {
    struct C2paReader *reader = (struct C2paReader*)readerPtr;
    char *json = c2pa_reader_json(reader);
    jstring result = cstring_to_jstring(env, json);
    c2pa_string_free(json);
    return result;
}

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_C2PAReader_resourceToStream(JNIEnv *env, jobject obj, jlong readerPtr, jstring uri, jlong streamPtr) {
    struct C2paReader *reader = (struct C2paReader*)readerPtr;
    const char *curi = jstring_to_cstring(env, uri);
    struct C2paStream *stream = (struct C2paStream*)streamPtr;
    
    int64_t result = c2pa_reader_resource_to_stream(reader, curi, stream);
    
    release_cstring(env, uri, curi);
    
    return (jlong)result;
}

// Builder native methods
JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_C2PABuilder_fromJson(JNIEnv *env, jclass clazz, jstring manifestJson) {
    const char *cmanifestJson = jstring_to_cstring(env, manifestJson);
    struct C2paBuilder *builder = c2pa_builder_from_json(cmanifestJson);
    release_cstring(env, manifestJson, cmanifestJson);
    return (jlong)builder;
}

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_C2PABuilder_fromArchive(JNIEnv *env, jclass clazz, jlong streamPtr) {
    struct C2paStream *stream = (struct C2paStream*)streamPtr;
    return (jlong)c2pa_builder_from_archive(stream);
}

// Companion object methods
JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_C2PABuilder_00024Companion_nativeFromJson(JNIEnv *env, jobject obj, jstring manifestJson) {
    const char *cmanifestJson = jstring_to_cstring(env, manifestJson);
    struct C2paBuilder *builder = c2pa_builder_from_json(cmanifestJson);
    release_cstring(env, manifestJson, cmanifestJson);
    return (jlong)builder;
}

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_C2PABuilder_00024Companion_nativeFromArchive(JNIEnv *env, jobject obj, jlong streamPtr) {
    struct C2paStream *stream = (struct C2paStream*)streamPtr;
    return (jlong)c2pa_builder_from_archive(stream);
}

JNIEXPORT void JNICALL Java_info_guardianproject_c2pa_C2PABuilder_free(JNIEnv *env, jobject obj, jlong builderPtr) {
    if (builderPtr != 0) {
        c2pa_builder_free((struct C2paBuilder*)builderPtr);
    }
}

JNIEXPORT void JNICALL Java_info_guardianproject_c2pa_C2PABuilder_setNoEmbed(JNIEnv *env, jobject obj, jlong builderPtr) {
    c2pa_builder_set_no_embed((struct C2paBuilder*)builderPtr);
}

JNIEXPORT jint JNICALL Java_info_guardianproject_c2pa_C2PABuilder_setRemoteUrl(JNIEnv *env, jobject obj, jlong builderPtr, jstring remoteUrl) {
    const char *cremoteUrl = jstring_to_cstring(env, remoteUrl);
    int result = c2pa_builder_set_remote_url((struct C2paBuilder*)builderPtr, cremoteUrl);
    release_cstring(env, remoteUrl, cremoteUrl);
    return result;
}

JNIEXPORT jint JNICALL Java_info_guardianproject_c2pa_C2PABuilder_addResource(JNIEnv *env, jobject obj, jlong builderPtr, jstring uri, jlong streamPtr) {
    const char *curi = jstring_to_cstring(env, uri);
    struct C2paStream *stream = (struct C2paStream*)streamPtr;
    int result = c2pa_builder_add_resource((struct C2paBuilder*)builderPtr, curi, stream);
    release_cstring(env, uri, curi);
    return result;
}

JNIEXPORT jint JNICALL Java_info_guardianproject_c2pa_C2PABuilder_addIngredientFromStream(JNIEnv *env, jobject obj, jlong builderPtr, jstring ingredientJson, jstring format, jlong streamPtr) {
    const char *cingredientJson = jstring_to_cstring(env, ingredientJson);
    const char *cformat = jstring_to_cstring(env, format);
    struct C2paStream *stream = (struct C2paStream*)streamPtr;
    
    int result = c2pa_builder_add_ingredient_from_stream(
        (struct C2paBuilder*)builderPtr, cingredientJson, cformat, stream
    );
    
    release_cstring(env, ingredientJson, cingredientJson);
    release_cstring(env, format, cformat);
    
    return result;
}

JNIEXPORT jint JNICALL Java_info_guardianproject_c2pa_C2PABuilder_toArchive(JNIEnv *env, jobject obj, jlong builderPtr, jlong streamPtr) {
    struct C2paBuilder *builder = (struct C2paBuilder*)builderPtr;
    struct C2paStream *stream = (struct C2paStream*)streamPtr;
    return c2pa_builder_to_archive(builder, stream);
}

JNIEXPORT jobject JNICALL Java_info_guardianproject_c2pa_C2PABuilder_sign(JNIEnv *env, jobject obj, jlong builderPtr, jstring format, jlong sourceStreamPtr, jlong destStreamPtr, jlong signerPtr) {
    struct C2paBuilder *builder = (struct C2paBuilder*)builderPtr;
    const char *cformat = jstring_to_cstring(env, format);
    struct C2paStream *source = (struct C2paStream*)sourceStreamPtr;
    struct C2paStream *dest = (struct C2paStream*)destStreamPtr;
    struct C2paSigner *signer = (struct C2paSigner*)signerPtr;
    
    const unsigned char *manifestBytes = NULL;
    int64_t size = c2pa_builder_sign(builder, cformat, source, dest, signer, &manifestBytes);
    
    release_cstring(env, format, cformat);
    
    // Create result object
    jclass resultClass = (*env)->FindClass(env, "com/adobe/c2pa/C2PABuilder$SignResult");
    jmethodID constructor = (*env)->GetMethodID(env, resultClass, "<init>", "(J[B)V");
    
    jbyteArray jmanifestBytes = NULL;
    if (manifestBytes != NULL && size > 0) {
        jmanifestBytes = (*env)->NewByteArray(env, size);
        (*env)->SetByteArrayRegion(env, jmanifestBytes, 0, size, (const jbyte*)manifestBytes);
        c2pa_manifest_bytes_free(manifestBytes);
    }
    
    return (*env)->NewObject(env, resultClass, constructor, (jlong)size, jmanifestBytes);
}

// Signer native methods
JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_C2PASigner_fromInfo(JNIEnv *env, jclass clazz, jobject signerInfo) {
    // Get SignerInfo fields
    jclass signerInfoClass = (*env)->GetObjectClass(env, signerInfo);
    jfieldID algField = (*env)->GetFieldID(env, signerInfoClass, "alg", "Ljava/lang/String;");
    jfieldID signCertField = (*env)->GetFieldID(env, signerInfoClass, "signCert", "Ljava/lang/String;");
    jfieldID privateKeyField = (*env)->GetFieldID(env, signerInfoClass, "privateKey", "Ljava/lang/String;");
    jfieldID taUrlField = (*env)->GetFieldID(env, signerInfoClass, "taUrl", "Ljava/lang/String;");
    
    jstring jalg = (*env)->GetObjectField(env, signerInfo, algField);
    jstring jsignCert = (*env)->GetObjectField(env, signerInfo, signCertField);
    jstring jprivateKey = (*env)->GetObjectField(env, signerInfo, privateKeyField);
    jstring jtaUrl = (*env)->GetObjectField(env, signerInfo, taUrlField);
    
    const char *calg = jstring_to_cstring(env, jalg);
    const char *csignCert = jstring_to_cstring(env, jsignCert);
    const char *cprivateKey = jstring_to_cstring(env, jprivateKey);
    const char *ctaUrl = jstring_to_cstring(env, jtaUrl);
    
    struct C2paSignerInfo cSignerInfo = {
        .alg = calg,
        .sign_cert = csignCert,
        .private_key = cprivateKey,
        .ta_url = ctaUrl
    };
    
    struct C2paSigner *signer = c2pa_signer_from_info(&cSignerInfo);
    
    release_cstring(env, jalg, calg);
    release_cstring(env, jsignCert, csignCert);
    release_cstring(env, jprivateKey, cprivateKey);
    release_cstring(env, jtaUrl, ctaUrl);
    
    return (jlong)signer;
}

// Companion object method for C2PASigner
JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_C2PASigner_00024Companion_nativeFromInfo(JNIEnv *env, jobject obj, jobject signerInfo) {
    // Get SignerInfo fields
    jclass signerInfoClass = (*env)->GetObjectClass(env, signerInfo);
    jfieldID algField = (*env)->GetFieldID(env, signerInfoClass, "alg", "Ljava/lang/String;");
    jfieldID signCertField = (*env)->GetFieldID(env, signerInfoClass, "signCert", "Ljava/lang/String;");
    jfieldID privateKeyField = (*env)->GetFieldID(env, signerInfoClass, "privateKey", "Ljava/lang/String;");
    jfieldID taUrlField = (*env)->GetFieldID(env, signerInfoClass, "taUrl", "Ljava/lang/String;");
    
    jstring jalg = (*env)->GetObjectField(env, signerInfo, algField);
    jstring jsignCert = (*env)->GetObjectField(env, signerInfo, signCertField);
    jstring jprivateKey = (*env)->GetObjectField(env, signerInfo, privateKeyField);
    jstring jtaUrl = (*env)->GetObjectField(env, signerInfo, taUrlField);
    
    const char *calg = jstring_to_cstring(env, jalg);
    const char *csignCert = jstring_to_cstring(env, jsignCert);
    const char *cprivateKey = jstring_to_cstring(env, jprivateKey);
    const char *ctaUrl = jstring_to_cstring(env, jtaUrl);
    
    struct C2paSignerInfo cSignerInfo = {
        .alg = calg,
        .sign_cert = csignCert,
        .private_key = cprivateKey,
        .ta_url = ctaUrl
    };
    
    struct C2paSigner *signer = c2pa_signer_from_info(&cSignerInfo);
    
    release_cstring(env, jalg, calg);
    release_cstring(env, jsignCert, csignCert);
    release_cstring(env, jprivateKey, cprivateKey);
    release_cstring(env, jtaUrl, ctaUrl);
    
    return (jlong)signer;
}

JNIEXPORT jlong JNICALL Java_info_guardianproject_c2pa_C2PASigner_reserveSize(JNIEnv *env, jobject obj, jlong signerPtr) {
    return c2pa_signer_reserve_size((struct C2paSigner*)signerPtr);
}

JNIEXPORT void JNICALL Java_info_guardianproject_c2pa_C2PASigner_free(JNIEnv *env, jobject obj, jlong signerPtr) {
    if (signerPtr != 0) {
        c2pa_signer_free((struct C2paSigner*)signerPtr);
    }
}

// Ed25519 signing
JNIEXPORT jbyteArray JNICALL Java_info_guardianproject_c2pa_C2PA_ed25519Sign(JNIEnv *env, jclass clazz, jbyteArray data, jstring privateKey) {
    jsize dataSize = (*env)->GetArrayLength(env, data);
    jbyte *cdata = (*env)->GetByteArrayElements(env, data, NULL);
    const char *cprivateKey = jstring_to_cstring(env, privateKey);
    
    const unsigned char *signature = c2pa_ed25519_sign((const unsigned char*)cdata, dataSize, cprivateKey);
    
    jbyteArray result = NULL;
    if (signature != NULL) {
        // Ed25519 signatures are always 64 bytes
        result = (*env)->NewByteArray(env, 64);
        (*env)->SetByteArrayRegion(env, result, 0, 64, (const jbyte*)signature);
        c2pa_signature_free(signature);
    }
    
    (*env)->ReleaseByteArrayElements(env, data, cdata, JNI_ABORT);
    release_cstring(env, privateKey, cprivateKey);
    
    return result;
}
