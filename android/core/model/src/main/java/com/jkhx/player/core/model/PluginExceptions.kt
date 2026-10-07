package com.jkhx.player.core.model

sealed class PluginException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PluginLoadError(pluginId: String, message: String, cause: Throwable? = null) :
    PluginException("Failed to load plugin [$pluginId]: $message", cause)

class PluginExecutionError(pluginId: String, method: String, message: String, cause: Throwable? = null) :
    PluginException("Error executing [$method] on plugin [$pluginId]: $message", cause)

class PluginTimeout(pluginId: String, method: String, timeoutMs: Long) :
    PluginException("Execution timeout in [$pluginId.$method] after ${timeoutMs}ms")

class PluginNetworkError(pluginId: String, url: String, message: String, cause: Throwable? = null) :
    PluginException("Network error in plugin [$pluginId] requesting [$url]: $message", cause)

class PluginParseError(pluginId: String, message: String, cause: Throwable? = null) :
    PluginException("Failed to parse data in plugin [$pluginId]: $message", cause)

class InvalidPluginManifest(message: String, cause: Throwable? = null) :
    PluginException("Invalid plugin manifest: $message", cause)

class UnsupportedPluginApi(pluginId: String, api: String) :
    PluginException("Plugin [$pluginId] attempted to call unsupported API [$api]")

class StreamResolveError(pluginId: String, message: String, cause: Throwable? = null) :
    PluginException("Failed to resolve stream for [$pluginId]: $message", cause)

class EmbedResolutionError(embedUrl: String, message: String, cause: Throwable? = null) :
    PluginException("Failed to resolve embed [$embedUrl]: $message", cause)

class PlayerError(message: String, cause: Throwable? = null) :
    PluginException("Player error: $message", cause)
