package net.libxray

import libXray.DialerController
import android.net.VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import android.util.Log

class AndroidDialerController(private val vpnService: VpnService) : DialerController {

    override fun protectFd(fd: Long): Boolean {
        val socketFd = fd.toInt()
        val resp = vpnService.protect(socketFd)
        if (!resp) {
            Log.e("XrayVpnService", "Failed to protect fd: $socketFd")
        }
        return resp
    }
}