package com.mik.speedtest.core.log

class Logger {
    var log = ""

    fun l(s: String) {
        synchronized(this) {
            log += """${System.currentTimeMillis()} $s
"""
        }
    }
}