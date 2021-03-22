package com.mik.speedtest.core

import com.mik.speedtest.core.config.SpeedtestConfig
import com.mik.speedtest.core.config.TelemetryConfig
import com.mik.speedtest.core.serverSelector.ServerSelector
import com.mik.speedtest.core.serverSelector.TestPoint
import com.mik.speedtest.core.worker.SpeedtestWorker
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.*

class Speedtest {
    private val servers: ArrayList<TestPoint> = ArrayList()
    private lateinit var selectedServer: TestPoint
    private var config = SpeedtestConfig()
    private var telemetryConfig = TelemetryConfig()
    private var state = 0 //0=configs, 1=test points, 2=server selection, 3=ready, 4=testing, 5=finished
    private val mutex = Any()
    private var originalExtra: String? = ""
    fun setSpeedtestConfig(c: SpeedtestConfig) {
        synchronized(mutex) {
            check(state == 0) { "Cannot change config at this moment" }
            config = c.clone()
            val extra = config.telemetry_extra
            if (extra != null && !extra.isEmpty()) originalExtra = extra
        }
    }

    fun setTelemetryConfig(c: TelemetryConfig) {
        synchronized(mutex) {
            check(state == 0) { "Cannot change config at this moment" }
            telemetryConfig = c.clone()
        }
    }

    fun addTestPoint(t: TestPoint) {
        synchronized(mutex) {
            if (state == 0) state = 1
            check(state <= 1) { "Cannot add test points at this moment" }
            servers.add(t)
        }
    }

    fun addTestPoints(s: Array<TestPoint>) {
        synchronized(mutex) { for (t in s) addTestPoint(t) }
    }

    fun addTestPoint(json: JSONObject?) {
        synchronized(mutex) { addTestPoint(TestPoint(json!!)) }
    }

    fun addTestPoints(json: JSONArray) {
        synchronized(mutex) {
            for (i in 0 until json.length()) try {
                addTestPoint(json.getJSONObject(i))
            } catch (t: JSONException) {
            }
        }
    }

    private object ServerListLoader {
        private fun read(url: String): String? {
            return try {
                val u = URL(url)
                val `in` = u.openStream()
                val br = BufferedReader(InputStreamReader(u.openStream()))
                var s: String? = ""
                try {
                    while (true) {
                        val r = br.readLine()
                        s += r ?: break
                    }
                } catch (t: Throwable) {
                }
                br.close()
                `in`.close()
                s
            } catch (t: Throwable) {
                null
            }
        }

        fun loadServerList(url: String): Array<TestPoint>? {
            return try {
                var s: String? = null
                if (url.startsWith("//")) {
                    s = read("https:$url")
                    if (s == null) s = read("http:$url")
                } else s = read(url)
                if (s == null) throw Exception("Failed")
                val a = JSONArray(s)
                val ret: ArrayList<TestPoint> = ArrayList()
                for (i in 0 until a.length()) {
                    ret.add(TestPoint(a.getJSONObject(i)))
                }
                ret.toTypedArray()
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun loadServerList(url: String): Boolean {
        synchronized(mutex) {
            if (state == 0) state = 1
            check(state <= 1) { "Cannot add test points at this moment" }
            val pts = ServerListLoader.loadServerList(url)
            return if (pts != null) {
                addTestPoints(pts)
                true
            } else false
        }
    }

    val testPoints: Array<TestPoint>
        get() {
            synchronized(mutex) { return servers.toTypedArray() }
        }

    private var ss: ServerSelector? = null
    fun selectServer(callback: ServerSelectedHandler) {
        synchronized(mutex) {
            check(state != 0) { "No test points added" }
            check(state != 2) { "Server selection is in progress" }
            check(state <= 2) { "Server already selected" }
            state = 2
            ss = object : ServerSelector(testPoints, config.ping_connectTimeout) {
                override fun onServerSelected(server: TestPoint?) {
                    selectedServer = server!!
                    synchronized(mutex) { state = if (server != null) 3 else 1 }
                    callback.onServerSelected(server)
                }
            }
            (ss as ServerSelector).start()
        }
    }

    fun setSelectedServer(t: TestPoint?) {
        synchronized(mutex) {
            check(state != 2) { "Server selection is in progress" }
            requireNotNull(t) { "t is null" }
            selectedServer = t
            state = 3
        }
    }

    private var st: SpeedtestWorker? = null
    fun start(callback: SpeedtestHandler) {
        synchronized(mutex) {
            check(state >= 3) { "Server hasn't been selected yet" }
            check(state != 4) { "Test already running" }
            state = 4
            try {
                val extra = JSONObject()
                if (originalExtra != null && !originalExtra!!.isEmpty()) extra.put("extra", originalExtra)
                extra.put("server", selectedServer!!.name)
                config.telemetry_extra = extra.toString()
            } catch (t: Throwable) {
            }
            st = object : SpeedtestWorker(selectedServer, config, telemetryConfig) {
                override fun onDownloadUpdate(dl: Double, progress: Double) {
                    callback.onDownloadUpdate(dl, progress)
                }

                override fun onUploadUpdate(ul: Double, progress: Double) {
                    callback.onUploadUpdate(ul, progress)
                }

                override fun onPingJitterUpdate(ping: Double, jitter: Double, progress: Double) {
                    callback.onPingJitterUpdate(ping, jitter, progress)
                }

                override fun onIPInfoUpdate(ipInfo: String?) {
                    callback.onIPInfoUpdate(ipInfo)
                }

                override fun onTestIDReceived(id: String?) {
                    var shareURL = prepareShareURL(telemetryConfig)
                    if (shareURL != null) shareURL = java.lang.String.format(shareURL, id)
                    callback.onTestIDReceived(id, shareURL)
                }

                override fun onEnd() {
                    synchronized(mutex) { this@Speedtest.state = 5 }
                    callback.onEnd()
                }

                override fun onCriticalFailure(err: String?) {
                    synchronized(mutex) { this@Speedtest.state = 5 }
                    callback.onCriticalFailure(err)
                }
            }
        }
    }

    private fun prepareShareURL(c: TelemetryConfig?): String? {
        if (c == null) return null
        var server = c.server
        var shareURL = c.shareURL
        if (server == null || server.isEmpty() || shareURL == null || shareURL.isEmpty()) return null
        if (!server.endsWith("/")) server = "$server/"
        while (shareURL!!.startsWith("/")) shareURL = shareURL.substring(1)
        if (server.startsWith("//")) server = "https:$server"
        return server + shareURL
    }

    fun abort() {
        synchronized(mutex) {
            if (state == 2) ss!!.stopASAP()
            if (state == 4) st!!.abort()
            state = 5
        }
    }

    abstract class ServerSelectedHandler {
        abstract fun onServerSelected(server: TestPoint?)
    }

    abstract class SpeedtestHandler {
        abstract fun onDownloadUpdate(dl: Double, progress: Double)
        abstract fun onUploadUpdate(ul: Double, progress: Double)
        abstract fun onPingJitterUpdate(ping: Double, jitter: Double, progress: Double)
        abstract fun onIPInfoUpdate(ipInfo: String?)
        abstract fun onTestIDReceived(id: String?, shareURL: String?)
        abstract fun onEnd()
        abstract fun onCriticalFailure(err: String?)
    }
}