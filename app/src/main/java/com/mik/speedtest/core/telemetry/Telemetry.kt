package com.mik.speedtest.core.telemetry

import com.mik.speedtest.core.base.Connection
import com.mik.speedtest.core.base.Utils.urlEncode
import com.mik.speedtest.core.config.TelemetryConfig

abstract class Telemetry(c: Connection, path: String, level: String, ispinfo: String, extra: String, dl: String, ul: String, ping: String, jitter: String, log: String) : Thread() {
    private lateinit var c: Connection
    private lateinit var path: String
    private lateinit var level: String
    private lateinit var ispinfo: String
    private lateinit var extra: String
    private lateinit var dl: String
    private lateinit var ul: String
    private lateinit var ping: String
    private lateinit var jitter: String
    private lateinit var log: String
    override fun run() {
        try {
            val s = path
            val sb = StringBuilder()
            sb.append("ispinfo=")
            sb.append(urlEncode(ispinfo))
            sb.append("&dl=")
            sb.append(urlEncode(dl))
            sb.append("&ul=")
            sb.append(urlEncode(ul))
            sb.append("&ping=")
            sb.append(urlEncode(ping))
            sb.append("&jitter=")
            sb.append(urlEncode(jitter))
            if (level == TelemetryConfig.LEVEL_FULL) {
                sb.append("&log=")
                sb.append(urlEncode(log))
            }
            sb.append("&extra=")
            sb.append(urlEncode(extra))
            c.POST(s, false, "application/x-www-form-urlencoded", sb.length.toLong())
            val ps = c.printStream
            ps!!.print(sb.toString())
            ps.flush()
            val h = c.parseResponseHeaders()
            var data: String? = ""
            val transferEncoding = h["transfer-encoding"]
            if (transferEncoding != null && transferEncoding.equals("chunked", ignoreCase = true)) {
                c.readLineUnbuffered()
            }
            data = c.readLineUnbuffered()
            onDataReceived(data)
            c.close()
        } catch (t: Throwable) {
            try {
                c.close()
            } catch (t1: Throwable) {
            }
            onError(t.toString())
        }
    }

    abstract fun onDataReceived(data: String?)
    abstract fun onError(err: String?)

    init {
        run {
            if (level == TelemetryConfig.LEVEL_DISABLED) {
                onDataReceived(null)
                return@run
            }
            this.c = c
            this.path = path
            this.level = level
            this.ispinfo = ispinfo
            this.extra = extra
            this.dl = dl
            this.ul = ul
            this.ping = ping
            this.jitter = jitter
            this.log = log
            start()
        }

    }
}