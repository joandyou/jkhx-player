# JKHX Player Security & Sandbox Architecture

Because JKHX Player executes third-party and community JavaScript plugins, security and sandboxing are top priorities. This document describes the defensive measures enforced at the runtime, JNI, and Android OS levels.

---

## 1. QuickJS-NG Sandbox Boundaries

Plugins run in a strictly constrained native C environment. QuickJS-NG does not have access to:
- Android Java runtime classes (`java.lang.*`, `android.os.*`).
- Native filesystem access (`libc fopen`, `open`, `unlink`).
- Local sockets or process spawning (`fork`, `exec`, `system`).
- Android private app directories or keystores.

All I/O must pass through the explicit native bridge registered in `quickjs_jni.c`.

---

## 2. Resource Quotas & Constraints

### 2.1. Memory Limit: 64 MB
Every `JSRuntime` instance enforces a hard memory allocation ceiling:
```c
JS_SetMemoryLimit(rt, 64 * 1024 * 1024); // 64 MB limit
```
If a plugin leaks memory or attempts to allocate oversized buffers, QuickJS-NG returns `JS_EXCEPTION` ("out of memory"), preventing Android out-of-memory (OOM) crashes of the host process.

### 2.2. Stack Size Limit: 1 MB
To prevent stack exhaustion via infinite recursion:
```c
JS_SetMaxStackSize(rt, 1024 * 1024); // 1 MB stack limit
```

### 2.3. Execution Timeout: 15 Seconds
To protect against infinite loops (`while (true) {}`) or unresponsive scripts, an interrupt callback is installed:
```c
static int js_interrupt_handler(JSRuntime *rt, void *opaque) {
    int64_t now = get_current_time_ms();
    if (now - start_time > timeout_ms) {
        return 1; // Abort execution with timeout
    }
    return 0;
}
```
If a script runs longer than 15,000 ms, execution is terminated immediately and a `PluginTimeoutException` is propagated to Kotlin.

---

## 3. Storage Scoping & Isolation

The `globalThis.context.storage` API is scoped per plugin ID:
```kotlin
fun getStorage(pluginId: String, key: String): String?
fun setStorage(pluginId: String, key: String, value: String)
```
A plugin cannot read, overwrite, or delete keys created by other plugins.

---

## 4. Network Security & Safe Mode

### 4.1. Network Security Config
The Android manifest restricts cleartext HTTP traffic to permitted domains, while enabling TLS 1.3 by default.

### 4.2. Safe Mode (18+ Adult Filter)
- The app includes a global **Safe Mode** toggle in Settings.
- When Safe Mode is enabled (default), any plugin with `"isAdult": true` in `manifest.json`:
  1. Is excluded from the Home aggregated feed.
  2. Is excluded from multi-provider search results.
  3. Is disabled by default upon initial installation.

