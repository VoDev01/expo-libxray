package net.libxray

import libXray.DialerController

class AndroidDialerController(private val vpnService: XrayVpnService) : DialerController {
    
    override fun protectFd(fd: Long): Boolean {
        val socketFd = fd.toInt()
        
        return vpnService.protect(socketFd)
    }
}