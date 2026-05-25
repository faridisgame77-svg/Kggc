package com.example.server

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

class PlaybackServer(
    private val port: Int = 8080,
    private val callback: ServerCallback
) {
    interface ServerCallback {
        fun onPlay(url: String)
        fun onPause()
        fun onResume()
        fun onStop()
        fun onVolume(level: Float)
        fun onSeek(positionMs: Long)
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        serverJob = scope.launch {
            try {
                Log.d("PlaybackServer", "Starting server on port $port...")
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                while (isRunning) {
                     val clientSocket = serverSocket?.accept() ?: break
                     scope.launch {
                         handleClient(clientSocket)
                     }
                }
            } catch (e: Exception) {
                Log.e("PlaybackServer", "Server socket exception", e)
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("PlaybackServer", "Error closing server socket", e)
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private fun handleClient(socket: Socket) {
        var reader: BufferedReader? = null
        var output: OutputStream? = null
        try {
            reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            Log.d("PlaybackServer", "Request: $requestLine")

            // Parse request line, e.g., "GET /play?url=http://example.com/stream.m3u8 HTTP/1.1"
            val parts = requestLine.split(" ")
            if (parts.size >= 2) {
                val method = parts[0]
                val pathAndQuery = parts[1]

                val cleanPath = pathAndQuery.substringBefore("?")
                val queryString = if (pathAndQuery.contains("?")) pathAndQuery.substringAfter("?") else ""

                // Extract query params
                val params = parseQuery(queryString)

                var responseBody = "{\"status\":\"ok\"}"
                var isCommandRecognized = true

                when (cleanPath) {
                    "/play" -> {
                        val encodedUrl = params["url"]
                        if (encodedUrl != null) {
                            val decodedUrl = URLDecoder.decode(encodedUrl, "UTF-8")
                            Log.d("PlaybackServer", "Command: Play stream URL -> $decodedUrl")
                            callback.onPlay(decodedUrl)
                        } else {
                            responseBody = "{\"status\":\"error\",\"message\":\"Missing url parameter\"}"
                        }
                    }
                    "/pause" -> {
                        Log.d("PlaybackServer", "Command: Pause")
                        callback.onPause()
                    }
                    "/resume" -> {
                        Log.d("PlaybackServer", "Command: Resume")
                        callback.onResume()
                    }
                    "/stop" -> {
                        Log.d("PlaybackServer", "Command: Stop")
                        callback.onStop()
                    }
                    "/volume" -> {
                        val levelStr = params["level"]
                        val level = levelStr?.toFloatOrNull()
                        if (level != null) {
                            Log.d("PlaybackServer", "Command: Volume -> $level")
                            callback.onVolume(level)
                        } else {
                            responseBody = "{\"status\":\"error\",\"message\":\"Missing or invalid level parameter\"}"
                        }
                    }
                    "/seek" -> {
                        val posStr = params["position"]
                        val pos = posStr?.toLongOrNull()
                        if (pos != null) {
                            Log.d("PlaybackServer", "Command: Seek -> $pos ms")
                            callback.onSeek(pos)
                        } else {
                            responseBody = "{\"status\":\"error\",\"message\":\"Missing or invalid position parameter\"}"
                        }
                    }
                    "/" -> {
                        responseBody = "{\"status\":\"running\",\"message\":\"EchoPlayer Sync Server is active\"}"
                    }
                    else -> {
                        isCommandRecognized = false
                    }
                }

                if (isCommandRecognized) {
                    sendHttpResponse(output, 200, "OK", responseBody)
                } else {
                    sendHttpResponse(output, 404, "Not Found", "{\"status\":\"error\",\"message\":\"Command not found\"}")
                }
            }
        } catch (e: Exception) {
            Log.e("PlaybackServer", "Client handler error", e)
        } finally {
            try {
                reader?.close()
                output?.close()
                socket.close()
            } catch (e: Exception) {
                Log.e("PlaybackServer", "Error closing client resources", e)
            }
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (query.isEmpty()) return result
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0 && idx < pair.length - 1) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                result[key] = value
            } else if (idx > 0) {
                result[pair.substring(0, idx)] = ""
            }
        }
        return result
    }

    private fun sendHttpResponse(output: OutputStream, statusCode: Int, statusMessage: String, body: String) {
        val responseBytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode $statusMessage\r\n" +
                "Content-Type: application/json\r\n" +
                "Access-Control-Allow-Origin: *\n" +
                "Content-Length: ${responseBytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(responseBytes)
        output.flush()
    }
}
