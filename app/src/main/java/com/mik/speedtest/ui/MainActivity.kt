package com.mik.speedtest.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import com.dosse.speedtest.R
import com.mik.speedtest.core.Speedtest
import com.mik.speedtest.core.Speedtest.ServerSelectedHandler
import com.mik.speedtest.core.Speedtest.SpeedtestHandler
import com.mik.speedtest.core.config.SpeedtestConfig
import com.mik.speedtest.core.config.TelemetryConfig
import com.mik.speedtest.core.serverSelector.TestPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.EOFException
import java.io.InputStreamReader
import java.util.*
import kotlin.jvm.Throws

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        transition(R.id.page_splash, 0)
        object : Thread() {
            override fun run() {
                try {
                    sleep(1500)
                } catch (t: Throwable) {
                }
                try {
                    val options = BitmapFactory.Options()
                    val v = findViewById<View>(R.id.testBackground) as ImageView
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeResource(resources, R.drawable.testbackground, options)
                    val ih = options.outHeight
                    val iw = options.outWidth
                    if (4 * ih * iw > 16 * 1024 * 1024) throw Exception("Too big")
                    options.inJustDecodeBounds = false
                    val displayMetrics = DisplayMetrics()
                    windowManager.defaultDisplay.getMetrics(displayMetrics)
                    val vh = displayMetrics.heightPixels
                    val vw = displayMetrics.widthPixels
                    val desired = Math.max(vw, vh) * 0.7
                    val scale = desired / Math.max(iw, ih)
                    val b = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, R.drawable.testbackground, options), (iw * scale).toInt(), (ih * scale).toInt(), true)
                    runOnUiThread { v.setImageBitmap(b) }
                } catch (t: Throwable) {
                    System.err.println("Failed to load testbackground (" + t.message + ")")
                }
                page_init()
            }
        }.start()
    }

    private fun page_init() {
        object : Thread() {
            override fun run() {
                runOnUiThread { transition(R.id.page_init, TRANSITION_LENGTH) }
                val t = findViewById<View>(R.id.init_text) as TextView
                runOnUiThread { t.setText(R.string.init_init) }
                var config: SpeedtestConfig? = null
                var telemetryConfig: TelemetryConfig? = null
                var servers: Array<TestPoint>? = null
                try {
                    var c = readFileFromAssets("SpeedtestConfig.json")
                    var o = JSONObject(c)
                    config = SpeedtestConfig(o)
                    c = readFileFromAssets("TelemetryConfig.json")
                    o = JSONObject(c)
                    telemetryConfig = TelemetryConfig(o)
                    if (telemetryConfig.telemetryLevel == TelemetryConfig.LEVEL_DISABLED) {
                        runOnUiThread { hideView(R.id.privacy_open) }
                    }
                    if (st != null) {
                        try {
                            st!!.abort()
                        } catch (e: Throwable) {
                        }
                    }
                    st = Speedtest()
                    st!!.setSpeedtestConfig(config)
                    st!!.setTelemetryConfig(telemetryConfig)
                    c = readFileFromAssets("ServerList.json")
                    if (c.startsWith("\"") || c.startsWith("'")) { //fetch server list from URL
                        if (!st!!.loadServerList(c.subSequence(1, c.length - 1).toString())) {
                            throw Exception("Failed to load server list")
                        }
                    } else { //use provided server list
                        val a = JSONArray(c)
                        if (a.length() == 0) throw Exception("No test points")
                        val s = ArrayList<TestPoint>()
                        for (i in 0 until a.length()) s.add(TestPoint(a.getJSONObject(i)))
                        servers = s.toTypedArray()
                        st!!.addTestPoints(servers)
                    }
                    val testOrder = config.getTest_order()
                    runOnUiThread {
                        if (!testOrder.contains("D")) {
                            hideView(R.id.dlArea)
                        }
                        if (!testOrder.contains("U")) {
                            hideView(R.id.ulArea)
                        }
                        if (!testOrder.contains("P")) {
                            hideView(R.id.pingArea)
                        }
                        if (!testOrder.contains("I")) {
                            hideView(R.id.ipInfo)
                        }
                    }
                } catch (e: Throwable) {
                    System.err.println(e)
                    st = null
                    transition(R.id.page_fail, TRANSITION_LENGTH)
                    runOnUiThread {
                        (findViewById<View>(R.id.fail_text) as TextView).text = getString(R.string.initFail_configError) + ": " + e.message
                        val b = findViewById<View>(R.id.fail_button) as Button
                        b.setText(R.string.initFail_retry)
                        b.setOnClickListener {
                            page_init()
                            b.setOnClickListener(null)
                        }
                    }
                    return
                }
                runOnUiThread { t.setText(R.string.init_selecting) }
                st!!.selectServer(object : ServerSelectedHandler() {
                    override fun onServerSelected(server: TestPoint?) {
                        runOnUiThread {
                            if (server == null) {
                                transition(R.id.page_fail, TRANSITION_LENGTH)
                                (findViewById<View>(R.id.fail_text) as TextView).text = getString(R.string.initFail_noServers)
                                val b = findViewById<View>(R.id.fail_button) as Button
                                b.setText(R.string.initFail_retry)
                                b.setOnClickListener {
                                    page_init()
                                    b.setOnClickListener(null)
                                }
                            } else {
                                page_serverSelect(server, st!!.testPoints)
                            }
                        }
                    }
                })
            }
        }.start()
    }

    private fun page_serverSelect(selected: TestPoint, servers: Array<TestPoint>) {
        transition(R.id.page_serverSelect, TRANSITION_LENGTH)
        reinitOnResume = true
        val availableServers = ArrayList<TestPoint>()
        for (t in servers) {
            if (t.ping != -1f) availableServers.add(t)
        }
        val selectedId = availableServers.indexOf(selected)
        val spinner = findViewById<View>(R.id.serverList) as Spinner
        val options = ArrayList<String>()
        for (t in availableServers) {
            options.add(t.name!!)
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options.toTypedArray())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val p = availableServers[position]
                if (p.sponsorName != null) {
                    findViewById<View>(R.id.sponsorArea).visibility = View.VISIBLE
                    val sponsor = findViewById<View>(R.id.sponsor) as TextView
                    if (p.sponsorURL != null) {
                        sponsor.setTextColor(resources.getColor(R.color.sponsorLinkColor))
                        sponsor.text = p.sponsorName
                        sponsor.paintFlags = sponsor.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                        sponsor.setOnClickListener {
                            var url = p.sponsorURL
                            if (url!!.startsWith("//")) url = "https:$url"
                            val i = Intent(Intent.ACTION_VIEW)
                            i.data = Uri.parse(url)
                            startActivity(i)
                        }
                    } else {
                        sponsor.text = p.sponsorName
                        sponsor.setTextColor(resources.getColor(R.color.textColor))
                        sponsor.setOnClickListener(null)
                        sponsor.paintFlags = sponsor.paintFlags and (Paint.UNDERLINE_TEXT_FLAG xor -0x1)
                    }
                } else {
                    findViewById<View>(R.id.sponsorArea).visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                findViewById<View>(R.id.sponsorArea).visibility = View.GONE
            }
        }
        spinner.setSelection(selectedId)
        val b = findViewById<View>(R.id.start) as Button
        b.setOnClickListener {
            reinitOnResume = false
            page_test(availableServers[spinner.selectedItemPosition])
            b.setOnClickListener(null)
        }
        val t = findViewById<View>(R.id.privacy_open) as TextView
        t.setOnClickListener { page_privacy() }
    }

    private fun page_privacy() {
        transition(R.id.page_privacy, TRANSITION_LENGTH)
        reinitOnResume = false
        (findViewById<View>(R.id.privacy_policy) as WebView).loadUrl(getString(R.string.privacy_policy))
        val t = findViewById<View>(R.id.privacy_close) as TextView
        t.setOnClickListener {
            transition(R.id.page_serverSelect, TRANSITION_LENGTH)
            reinitOnResume = true
        }
    }

    private fun page_test(selected: TestPoint) {
        transition(R.id.page_test, TRANSITION_LENGTH)
        st!!.setSelectedServer(selected)
        (findViewById<View>(R.id.serverName) as TextView).text = selected.name
        (findViewById<View>(R.id.dlText) as TextView).text = format(0.0)
        (findViewById<View>(R.id.ulText) as TextView).text = format(0.0)
        (findViewById<View>(R.id.pingText) as TextView).text = format(0.0)
        (findViewById<View>(R.id.jitterText) as TextView).text = format(0.0)
        (findViewById<View>(R.id.dlProgress) as ProgressBar).progress = 0
        (findViewById<View>(R.id.ulProgress) as ProgressBar).progress = 0
        (findViewById<View>(R.id.dlGauge) as GaugeView).setValue(0)
        (findViewById<View>(R.id.ulGauge) as GaugeView).setValue(0)
        (findViewById<View>(R.id.ipInfo) as TextView).text = ""
        (findViewById<View>(R.id.logo_inapp) as ImageView).setOnClickListener(View.OnClickListener {
            val url = getString(R.string.logo_inapp_link)
            if (url.isEmpty()) return@OnClickListener
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
        })
        val endTestArea = findViewById<View>(R.id.endTestArea)
        val endTestAreaHeight = endTestArea.height
        val p = endTestArea.layoutParams
        p.height = 0
        endTestArea.layoutParams = p
        findViewById<View>(R.id.shareButton).visibility = View.GONE
        st!!.start(object : SpeedtestHandler() {
            override fun onDownloadUpdate(dl: Double, progress: Double) {
                runOnUiThread {
                    (findViewById<View>(R.id.dlText) as TextView).text = if (progress == 0.0) "..." else format(dl)
                    (findViewById<View>(R.id.dlGauge) as GaugeView).setValue(if (progress == 0.0) 0 else mbpsToGauge(dl))
                    (findViewById<View>(R.id.dlProgress) as ProgressBar).progress = (100 * progress).toInt()
                }
            }

            override fun onUploadUpdate(ul: Double, progress: Double) {
                runOnUiThread {
                    (findViewById<View>(R.id.ulText) as TextView).text = if (progress == 0.0) "..." else format(ul)
                    (findViewById<View>(R.id.ulGauge) as GaugeView).setValue(if (progress == 0.0) 0 else mbpsToGauge(ul))
                    (findViewById<View>(R.id.ulProgress) as ProgressBar).progress = (100 * progress).toInt()
                }
            }

            override fun onPingJitterUpdate(ping: Double, jitter: Double, progress: Double) {
                runOnUiThread {
                    (findViewById<View>(R.id.pingText) as TextView).text = if (progress == 0.0) "..." else format(ping)
                    (findViewById<View>(R.id.jitterText) as TextView).text = if (progress == 0.0) "..." else format(jitter)
                }
            }

            override fun onIPInfoUpdate(ipInfo: String?) {
                runOnUiThread { (findViewById<View>(R.id.ipInfo) as TextView).text = ipInfo }
            }

            override fun onTestIDReceived(id: String?, shareURL: String?) {
                if (shareURL == null || shareURL.isEmpty() || id == null || id.isEmpty()) return
                runOnUiThread {
                    val shareButton = findViewById<View>(R.id.shareButton) as Button
                    shareButton.visibility = View.VISIBLE
                    shareButton.setOnClickListener {
                        val share = Intent(Intent.ACTION_SEND)
                        share.type = "text/plain"
                        share.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                        share.putExtra(Intent.EXTRA_TEXT, shareURL)
                        startActivity(Intent.createChooser(share, getString(R.string.test_share)))
                    }
                }
            }

            override fun onEnd() {
                runOnUiThread {
                    val restartButton = findViewById<View>(R.id.restartButton) as Button
                    restartButton.setOnClickListener {
                        page_init()
                        restartButton.setOnClickListener(null)
                    }
                }
                val startT = System.currentTimeMillis()
                val endT = startT + TRANSITION_LENGTH
                object : Thread() {
                    override fun run() {
                        while (System.currentTimeMillis() < endT) {
                            val f = (System.currentTimeMillis() - startT).toDouble() / (endT - startT).toDouble()
                            runOnUiThread {
                                val p = endTestArea.layoutParams
                                p.height = (endTestAreaHeight * f).toInt()
                                endTestArea.layoutParams = p
                            }
                            try {
                                sleep(10)
                            } catch (t: Throwable) {
                            }
                        }
                    }
                }.start()
            }

            override fun onCriticalFailure(err: String?) {
                runOnUiThread {
                    transition(R.id.page_fail, TRANSITION_LENGTH)
                    (findViewById<View>(R.id.fail_text) as TextView).text = getString(R.string.testFail_err)
                    val b = findViewById<View>(R.id.fail_button) as Button
                    b.setText(R.string.testFail_retry)
                    b.setOnClickListener {
                        page_init()
                        b.setOnClickListener(null)
                    }
                }
            }
        })
    }

    private fun format(d: Double): String {
        var l: Locale? = null
        l = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0]
        } else {
            resources.configuration.locale
        }
        if (d < 10) return String.format(l, "%.2f", d)
        return if (d < 100) String.format(l, "%.1f", d) else "" + Math.round(d)
    }

    private fun mbpsToGauge(s: Double): Int {
        return (1000 * (1 - 1 / Math.pow(1.3, Math.sqrt(s)))).toInt()
    }

    @Throws(Exception::class)
    private fun readFileFromAssets(name: String): String {
        val b = BufferedReader(InputStreamReader(assets.open(name)))
        var ret = ""
        try {
            while (true) {
                val s = b.readLine() ?: break
                ret += s
            }
        } catch (e: EOFException) {
        }
        return ret
    }

    private fun hideView(id: Int) {
        val v = findViewById<View>(id)
        if (v != null) v.visibility = View.GONE
    }

    private var reinitOnResume = false
    override fun onResume() {
        super.onResume()
        if (reinitOnResume) {
            reinitOnResume = false
            page_init()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            st!!.abort()
        } catch (t: Throwable) {
        }
    }

    override fun onBackPressed() {
        if (currentPage == R.id.page_privacy) transition(R.id.page_serverSelect, TRANSITION_LENGTH) else super.onBackPressed()
    }

    //PAGE TRANSITION SYSTEM
    private var currentPage = -1
    private var transitionBusy = false //TODO: improve mutex
    private val TRANSITION_LENGTH = 300
    private fun transition(page: Int, duration: Int) {
        if (transitionBusy) {
            object : Thread() {
                override fun run() {
                    try {
                        sleep(10)
                    } catch (t: Throwable) {
                    }
                    transition(page, duration)
                }
            }.start()
        } else transitionBusy = true
        if (page == currentPage) return
        val oldPage = if (currentPage == -1) null else findViewById<View>(currentPage) as ViewGroup
        val newPage = if (page == -1) null else findViewById<View>(page) as ViewGroup
        object : Thread() {
            override fun run() {
                var t = System.currentTimeMillis()
                val endT = t + duration
                runOnUiThread {
                    if (newPage != null) {
                        newPage.alpha = 0f
                        newPage.visibility = View.VISIBLE
                    }
                    if (oldPage != null) {
                        oldPage.alpha = 1f
                    }
                }
                while (t < endT) {
                    t = System.currentTimeMillis()
                    val f = (endT - t).toFloat() / duration.toFloat()
                    runOnUiThread {
                        if (newPage != null) newPage.alpha = 1 - f
                        if (oldPage != null) oldPage.alpha = f
                    }
                    try {
                        sleep(10)
                    } catch (e: Throwable) {
                    }
                }
                currentPage = page
                runOnUiThread {
                    if (oldPage != null) {
                        oldPage.alpha = 0f
                        oldPage.visibility = View.INVISIBLE
                    }
                    if (newPage != null) {
                        newPage.alpha = 1f
                    }
                    transitionBusy = false
                }
            }
        }.start()
    }

    companion object {
        private var st: Speedtest? = null
    }
}