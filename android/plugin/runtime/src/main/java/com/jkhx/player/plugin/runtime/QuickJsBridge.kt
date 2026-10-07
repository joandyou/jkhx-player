package com.jkhx.player.plugin.runtime

class QuickJsBridge {
    companion object {
        init {
            try {
                System.loadLibrary("quickjs-ng")
            } catch (e: UnsatisfiedLinkError) {
                // In pure unit test environment without native lib loaded
            }
        }
    }

    external fun nativeCreateRuntime(memLimit: Long, stackSize: Long): Long
    external fun nativeDestroyRuntime(rtPtr: Long)
    external fun nativeCreateContext(rtPtr: Long): Long
    external fun nativeDestroyContext(ctxPtr: Long)
    external fun nativeSetTimeout(rtPtr: Long, timeoutMs: Long)
    external fun nativeEval(ctxPtr: Long, script: String, filename: String): String?
    external fun nativeRegisterBridge(ctxPtr: Long, bridgeHandler: Any)
}
