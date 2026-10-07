#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <time.h>
#include <stdbool.h>
#include <android/log.h>
#include "quickjs.h"

#define LOG_TAG "QuickJsJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef struct {
    JSRuntime *rt;
    int64_t deadline_ms;
    bool timeout_triggered;
    JavaVM *jvm;
    jobject bridge_handler;
} RuntimeUserData;

static int64_t get_time_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + (ts.tv_nsec / 1000000);
}

static int js_interrupt_handler(JSRuntime *rt, void *opaque) {
    RuntimeUserData *data = (RuntimeUserData *)opaque;
    if (data && data->deadline_ms > 0) {
        if (get_time_ms() > data->deadline_ms) {
            data->timeout_triggered = true;
            return 1; // Interrupted due to timeout!
        }
    }
    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_jkhx_player_plugin_runtime_QuickJsBridge_nativeCreateRuntime(
        JNIEnv *env, jobject thiz, jlong mem_limit, jlong stack_size) {
    JSRuntime *rt = JS_NewRuntime();
    if (!rt) return 0;

    if (mem_limit > 0) {
        JS_SetMemoryLimit(rt, (size_t)mem_limit);
    }
    if (stack_size > 0) {
        JS_SetMaxStackSize(rt, (size_t)stack_size);
    }

    RuntimeUserData *data = (RuntimeUserData *)calloc(1, sizeof(RuntimeUserData));
    data->rt = rt;
    (*env)->GetJavaVM(env, &data->jvm);
    JS_SetRuntimeOpaque(rt, data);
    JS_SetInterruptHandler(rt, js_interrupt_handler, data);

    return (jlong)rt;
}

JNIEXPORT void JNICALL
Java_com_jkhx_player_plugin_runtime_QuickJsBridge_nativeDestroyRuntime(
        JNIEnv *env, jobject thiz, jlong rt_ptr) {
    JSRuntime *rt = (JSRuntime *)rt_ptr;
    if (rt) {
        RuntimeUserData *data = (RuntimeUserData *)JS_GetRuntimeOpaque(rt);
        if (data) {
            if (data->bridge_handler) {
                JNIEnv *jenv = NULL;
                if ((*data->jvm)->GetEnv(data->jvm, (void **)&jenv, JNI_VERSION_1_6) == JNI_OK) {
                    (*jenv)->DeleteGlobalRef(jenv, data->bridge_handler);
                }
            }
            free(data);
        }
        JS_FreeRuntime(rt);
    }
}

JNIEXPORT jlong JNICALL
Java_com_jkhx_player_plugin_runtime_QuickJsBridge_nativeCreateContext(
        JNIEnv *env, jobject thiz, jlong rt_ptr) {
    JSRuntime *rt = (JSRuntime *)rt_ptr;
    if (!rt) return 0;
    JSContext *ctx = JS_NewContext(rt);
    return (jlong)ctx;
}

JNIEXPORT void JNICALL
Java_com_jkhx_player_plugin_runtime_QuickJsBridge_nativeDestroyContext(
        JNIEnv *env, jobject thiz, jlong ctx_ptr) {
    JSContext *ctx = (JSContext *)ctx_ptr;
    if (ctx) {
        JS_FreeContext(ctx);
    }
}

JNIEXPORT void JNICALL
Java_com_jkhx_player_plugin_runtime_QuickJsBridge_nativeSetTimeout(
        JNIEnv *env, jobject thiz, jlong rt_ptr, jlong timeout_ms) {
    JSRuntime *rt = (JSRuntime *)rt_ptr;
    if (rt) {
        RuntimeUserData *data = (RuntimeUserData *)JS_GetRuntimeOpaque(rt);
        if (data) {
            if (timeout_ms > 0) {
                data->deadline_ms = get_time_ms() + timeout_ms;
                data->timeout_triggered = false;
            } else {
                data->deadline_ms = 0;
                data->timeout_triggered = false;
            }
        }
    }
}

static char *get_exception_str(JSContext *ctx) {
    JSValue exception = JS_GetException(ctx);
    const char *str = JS_ToCString(ctx, exception);
    char *result = NULL;
    if (str) {
        result = strdup(str);
        JS_FreeCString(ctx, str);
    } else {
        result = strdup("Unknown JavaScript error");
    }
    JS_FreeValue(ctx, exception);
    return result;
}

// Executes all pending promise microtasks in the event loop
static void run_pending_jobs(JSContext *ctx) {
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSContext *pctx;
    while (JS_ExecutePendingJob(rt, &pctx) > 0) {
        // keep executing microtasks until queue is empty
    }
}

JNIEXPORT jstring JNICALL
Java_com_jkhx_player_plugin_runtime_QuickJsBridge_nativeEval(
        JNIEnv *env, jobject thiz, jlong ctx_ptr, jstring script_str, jstring filename_str) {
    JSContext *ctx = (JSContext *)ctx_ptr;
    if (!ctx) return NULL;

    const char *script = (*env)->GetStringUTFChars(env, script_str, NULL);
    const char *filename = (*env)->GetStringUTFChars(env, filename_str, NULL);

    int flags = JS_EVAL_TYPE_GLOBAL;
    JSValue val = JS_Eval(ctx, script, strlen(script), filename, flags);

    (*env)->ReleaseStringUTFChars(env, script_str, script);
    (*env)->ReleaseStringUTFChars(env, filename_str, filename);

    if (JS_IsException(val)) {
        char *err = get_exception_str(ctx);
        jclass ex_cls = (*env)->FindClass(env, "com/jkhx/player/core/model/PluginExecutionError");
        if (ex_cls) (*env)->ThrowNew(env, ex_cls, err);
        free(err);
        return NULL;
    }

    // Run microtasks
    run_pending_jobs(ctx);

    JSValue json_val = JS_JSONStringify(ctx, val, JS_UNDEFINED, JS_UNDEFINED);
    JS_FreeValue(ctx, val);

    if (JS_IsException(json_val)) {
        JS_FreeValue(ctx, json_val);
        return (*env)->NewStringUTF(env, "null");
    }

    const char *json_str = JS_ToCString(ctx, json_val);
    jstring result = (*env)->NewStringUTF(env, json_str ? json_str : "null");
    if (json_str) JS_FreeCString(ctx, json_str);
    JS_FreeValue(ctx, json_val);

    return result;
}

// Native Bridge Dispatcher from JS to Kotlin
static JSValue js_native_dispatch(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    if (argc < 2) return JS_UNDEFINED;
    JSRuntime *rt = JS_GetRuntime(ctx);
    RuntimeUserData *data = (RuntimeUserData *)JS_GetRuntimeOpaque(rt);
    if (!data || !data->jvm || !data->bridge_handler) return JS_UNDEFINED;

    const char *action = JS_ToCString(ctx, argv[0]);
    const char *payload = JS_ToCString(ctx, argv[1]);

    JNIEnv *env = NULL;
    (*data->jvm)->AttachCurrentThread(data->jvm, (void **)&env, NULL);

    jclass cls = (*env)->GetObjectClass(env, data->bridge_handler);
    jmethodID mid = (*env)->GetMethodID(env, cls, "onNativeCall", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");

    jstring jaction = (*env)->NewStringUTF(env, action ? action : "");
    jstring jpayload = (*env)->NewStringUTF(env, payload ? payload : "");

    jstring jres = (jstring)(*env)->CallObjectMethod(env, data->bridge_handler, mid, jaction, jpayload);

    const char *res_cstr = jres ? (*env)->GetStringUTFChars(env, jres, NULL) : "null";
    JSValue ret_val = JS_ParseJSON(ctx, res_cstr, strlen(res_cstr), "<bridge>");

    if (jres) (*env)->ReleaseStringUTFChars(env, jres, res_cstr);
    if (action) JS_FreeCString(ctx, action);
    if (payload) JS_FreeCString(ctx, payload);

    return ret_val;
}

JNIEXPORT void JNICALL
Java_com_jkhx_player_plugin_runtime_QuickJsBridge_nativeRegisterBridge(
        JNIEnv *env, jobject thiz, jlong ctx_ptr, jobject bridge_handler) {
    JSContext *ctx = (JSContext *)ctx_ptr;
    if (!ctx) return;

    JSRuntime *rt = JS_GetRuntime(ctx);
    RuntimeUserData *data = (RuntimeUserData *)JS_GetRuntimeOpaque(rt);
    if (data) {
        if (data->bridge_handler) {
            (*env)->DeleteGlobalRef(env, data->bridge_handler);
        }
        data->bridge_handler = (*env)->NewGlobalRef(env, bridge_handler);
    }

    JSValue global_obj = JS_GetGlobalObject(ctx);
    JSValue fn = JS_NewCFunction(ctx, js_native_dispatch, "__native_dispatch", 2);
    JS_SetPropertyStr(ctx, global_obj, "__native_dispatch", fn);
    JS_FreeValue(ctx, global_obj);
}
