package com.mik.speedtest.core.base

import android.os.Build
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.*
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory
import kotlin.jvm.Throws

class Connection @JvmOverloads constructor(url: String, connectTimeout: Int = DEFAULT_CONNECT_TIMEOUT, soTimeout: Int = DEFAULT_SO_TIMEOUT, recvBuffer: Int = -1, sendBuffer: Int = -1) {
    private var socket: Socket? = null
    var host: String? = null
    var port = 0
    var mode = MODE_NOT_SET
    val inputStream: InputStream?
        get() = try {
            socket!!.getInputStream()
        } catch (t: Throwable) {
            null
        }

    val outputStream: OutputStream?
        get() = try {
            socket!!.getOutputStream()
        } catch (t: Throwable) {
            null
        }

    private var ps: PrintStream? = null
    val printStream: PrintStream?
        get() {
            if (ps == null) {
                ps = try {
                    PrintStream(outputStream, false, "utf-8")
                } catch (t: Throwable) {
                    null
                }
            }
            return ps
        }

    private var isr: InputStreamReader? = null
    val inputStreamReader: InputStreamReader?
        get() {
            if (isr == null) {
                isr = try {
                    InputStreamReader(inputStream, "utf-8")
                } catch (t: Throwable) {
                    null
                }
            }
            return isr
        }

    @Throws(Exception::class)
    fun GET(path: String, keepAlive: Boolean) {
        var path = path
        try {
            if (!path.startsWith("/")) path = "/$path"
            val ps = printStream
            ps!!.print("GET $path HTTP/1.1\r\n")
            ps.print("Host: $host\r\n")
            ps.print("User-Agent: $USER_AGENT")
            ps.print("""
    Connection: ${if (keepAlive) "keep-alive" else "close"}
    
    """.trimIndent())
            ps.print("Accept-Encoding: identity\r\n")
            if (LOCALE != null) ps.print("Accept-Language: $LOCALE\r\n")
            ps.print("\r\n")
            ps.flush()
        } catch (t: Throwable) {
            throw Exception("Failed to send GET request")
        }
    }

    @Throws(Exception::class)
    fun POST(path: String, keepAlive: Boolean, contentType: String?, contentLength: Long) {
        var path = path
        try {
            if (!path.startsWith("/")) path = "/$path"
            val ps = printStream
            ps!!.print("POST $path HTTP/1.1\r\n")
            ps.print("Host: $host\r\n")
            ps.print("User-Agent: $USER_AGENT\r\n")
            ps.print("""
    Connection: ${if (keepAlive) "keep-alive" else "close"}
    
    """.trimIndent())
            ps.print("Accept-Encoding: identity\r\n")
            if (LOCALE != null) ps.print("Accept-Language: $LOCALE\r\n")
            if (contentType != null) ps.print("Content-Type: $contentType\r\n")
            ps.print("Content-Encoding: identity\r\n")
            if (contentLength >= 0) ps.print("Content-Length: $contentLength\r\n")
            ps.print("\r\n")
            ps.flush()
        } catch (t: Throwable) {
            throw Exception("Failed to send POST request")
        }
    }

    fun readLineUnbuffered(): String? {
        return try {
            val `in` = inputStreamReader
            val sb = StringBuilder()
            while (true) {
                val c = `in`!!.read()
                if (c == -1) break
                sb.append(c.toChar())
                if (c == '\n'.toInt()) break
            }
            sb.toString()
        } catch (t: Throwable) {
            null
        }
    }

    @Throws(Exception::class)
    fun parseResponseHeaders(): HashMap<String?, String?> {
        return try {
            val ret: HashMap<String?, String?> = HashMap()
            var s = readLineUnbuffered()
            if (!s!!.contains("200 OK")) throw Exception("Did not receive an HTTP 200 (" + s.trim { it <= ' ' } + ")")
            while (true) {
                s = readLineUnbuffered()
                if (s!!.trim { it <= ' ' }.isEmpty()) break
                if (s.contains(":")) {
                    ret[s.substring(0, s.indexOf(":")).trim { it <= ' ' }.toLowerCase()] = s.substring(s.indexOf(":") + 1).trim { it <= ' ' }
                }
            }
            ret
        } catch (t: Throwable) {
            throw Exception("Failed to get response headers ($t)")
        }
    }

    fun close() {
        try {
            socket!!.close()
        } catch (t: Throwable) {
        }
        socket = null
    }

    companion object {
        private const val MODE_NOT_SET = 0
        private const val MODE_HTTP = 1
        private const val MODE_HTTPS = 2
        private val USER_AGENT = "Speedtest-Android/1.2 (SDK " + Build.VERSION.SDK_INT + "; " + Build.PRODUCT + "; Android " + Build.VERSION.RELEASE + ")"
        private val LOCALE = if (Build.VERSION.SDK_INT >= 21) Locale.getDefault().toLanguageTag() else null
        private const val DEFAULT_CONNECT_TIMEOUT = 2000
        private const val DEFAULT_SO_TIMEOUT = 5000
    }

    init {
        var tryHTTP = false
        var tryHTTPS = false
        Locale.getDefault().toString()
        if (url.startsWith("http://")) {
            tryHTTP = true
            try {
                val u = URL(url)
                host = u.host
                port = u.port
            } catch (t: Throwable) {
                throw IllegalArgumentException("Malformed URL (HTTP)")
            }
        } else if (url.startsWith("https://")) {
            tryHTTPS = true
            try {
                val u = URL(url)
                host = u.host
                port = u.port
            } catch (t: Throwable) {
                throw IllegalArgumentException("Malformed URL (HTTPS)")
            }
        } else if (url.startsWith("//")) {
            tryHTTP = true
            tryHTTPS = true
            try {
                val u = URL("http:$url")
                host = u.host
                port = u.port
            } catch (t: Throwable) {
                throw IllegalArgumentException("Malformed URL (HTTP/HTTPS)")
            }
        } else {
            throw IllegalArgumentException("Malformed URL (Unknown or unspecified protocol)")
        }
        try {
            if (tryHTTPS) {
                val factory = SSLSocketFactory.getDefault()
                socket = factory.createSocket()
                if (connectTimeout > 0) {
                    socket!!.connect(InetSocketAddress(host, if (port == -1) 443 else port), connectTimeout)
                } else {
                    socket!!.connect(InetSocketAddress(host, if (port == -1) 443 else port))
                }
                mode = MODE_HTTPS
            }
        } catch (t: Throwable) {
        }
        try {
            if (tryHTTP) {
                val factory = SocketFactory.getDefault()
                socket = factory.createSocket()
                if (connectTimeout > 0) {
                    socket!!.connect(InetSocketAddress(host, if (port == -1) 80 else port), connectTimeout)
                } else {
                    socket!!.connect(InetSocketAddress(host, if (port == -1) 80 else port))
                }
                mode = MODE_HTTP
            }
        } catch (t: Throwable) {
        }
        check(mode != MODE_NOT_SET) { "Failed to connect" }
        if (soTimeout > 0) {
            try {
                socket!!.soTimeout = soTimeout
            } catch (t: Throwable) {
            }
        }
        if (recvBuffer > 0) {
            try {
                socket!!.receiveBufferSize = recvBuffer
            } catch (t: Throwable) {
            }
        }
        if (sendBuffer > 0) {
            try {
                socket!!.sendBufferSize = sendBuffer
            } catch (t: Throwable) {
            }
        }
    }
}