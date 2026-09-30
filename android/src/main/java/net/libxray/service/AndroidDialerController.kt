package net.libxray.service

import libXray.DialerController
import android.util.Log

class AndroidDialerController(private val vpnService: XrayVpnService) : DialerController {

    override fun protectFd(fd: Long): Boolean {
        if (!vpnService.isRunning) {
            return true
        }
        val socketFd = fd.toInt()
        val resp = vpnService.protect(socketFd)
        if (!resp) {
            Log.e("XrayVpnService", "Failed to protect fd: $socketFd")
        }
        return resp
    }
}