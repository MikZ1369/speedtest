package com.mik.speedtest.core.getIP

import com.mik.speedtest.core.base.Connection
import com.mik.speedtest.core.base.Utils.url_sep
import com.mik.speedtest.core.config.SpeedtestConfig
import java.io.BufferedReader

abstract class GetIP(private val c: Connection, private val path: String, private val isp: Boolean, distance: String?) : Thread() {
    private val distance: String?
    override fun run() {
        try {
            var s = path
            if (isp) {
                s += url_sep(s) + "isp=true"
                if (distance != SpeedtestConfig.DISTANCE_NO) {
                    s += url_sep(s) + "distance=" + distance
                }
            }
            c.GET(s, true)
            val h = c.parseResponseHeaders()
            val br = BufferedReader(c.inputStreamReader)
            if (h["content-length"] != null) {
                //standard encoding
                val buf = CharArray(h["content-length"]!!.toInt())
                br.read(buf)
                val data = String(buf)
                onDataReceived(data)
            } else {
                //chunked encoding hack. TODO: improve this garbage with proper chunked support
                c.readLineUnbuffered() //ignore first line
                val data = c.readLineUnbuffered() //actual info we want
                c.readLineUnbuffered() //ignore last line (0)
                onDataReceived(data)
            }
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
        require(distance == null || distance == SpeedtestConfig.DISTANCE_KM || distance == SpeedtestConfig.DISTANCE_MILES) { "Distance must be null, mi or km" }
        this.distance = distance
        start()
    }
}