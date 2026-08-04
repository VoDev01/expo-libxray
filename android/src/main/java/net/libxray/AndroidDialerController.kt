package net.libxray

import android.net.VpnService
import libXray.DialerController

class AndroidDialerController(private val vpnService: VpnService) : DialerController {
    
    override fun protectFd(fd: Long): Boolean {
        val socketFd = fd.toInt()
        
        return vpnService.protect(socketFd)
    }
}