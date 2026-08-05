package net.libxray

import org.json.JSONArray
import org.json.JSONObject
import android.content.Context

fun setFd(
    config: String,
    fd: Int
): String {
    val root = JSONObject(config)

    val env = if (root.has("env") && !root.isNull("env")) {
        root.getJSONObject("env")
    } else {
        JSONObject()
    }

    env.put("xray.tun.fd", fd.toString())
    root.put("env", env)

    return root.toString()
}

fun addSocksInboundToClientXrayConfig(
    context: Context,
    initialConf: String,
    listenIp: String = "127.0.0.1",
    serverPort: Int = 10808,
    inboundTag: String = "SOCKS LOCAL",
    outboundTag: String = "VLESS TCP REALITY",
    address: String? = null,
    sendThrough: String = "0.0.0.0"
): String {
    val root = JSONObject(initialConf)
    
    configureLogging(root, context)

    val inboundsArray = JSONArray()
    val socksInbound = JSONObject().apply {
        put("tag", inboundTag)
        put("listen", listenIp)
        put("port", serverPort)
        put("protocol", "socks")
        val inSettings = JSONObject().apply {
            put("auth", "noauth")
            put("udp", true)
            put("ip", listenIp)
            put("userLevel", 0)
        }
        put("settings", inSettings)
    }

    inboundsArray.put(socksInbound)

    root.put("inbounds", inboundsArray)

    configureOutbounds(
        root,
        outboundTag,
        sendThrough,
        address
    )

    configureRouting(
        root, 
        inboundTag, 
        outboundTag
    )

    return root.toString()
}

fun addTUNInboundToClientXrayConfig(
    context: Context,
    initialConf: String,
    inboundTag: String = "TUN LOCAL",
    outboundTag: String = "VLESS TCP REALITY",
    address: String = "172.19.0.1/30",
    gateway: String = "172.19.0.2",
    dns: String = "8.8.8.8",
    sendThrough: String = "0.0.0.0",
    outboundAddress: String? = "10.0.2.2"
): String {
    val root = JSONObject(initialConf)
    
    configureLogging(root, context)

    val inboundsArray = JSONArray()
    val tunInbound = JSONObject().apply {
        put("tag", inboundTag)
        put("protocol", "tun")
        put("interfaceName", "tun0")
        val inSettings = JSONObject().apply {
            put("mtu", 1500)
            put("address", JSONArray().put(address))
            put("gateway", JSONArray().put(gateway))
            put("dns", JSONArray().put(dns))
            put("autoRoute", false)
        }
        put("settings", inSettings)
    }

    inboundsArray.put(tunInbound)

    root.put("inbounds", inboundsArray)

    configureOutbounds(
        root, 
        outboundTag, 
        sendThrough,
        outboundAddress
    )

    return root.toString()
}

private fun configureLogging(
    root: JSONObject,
    context: Context
) {
    val logBlock = JSONObject().apply {
        put("loglevel", "debug")
        put("error", "${context.filesDir.absolutePath}/xray_error.log")
        put("maskAddress", "half")
    }

    root.put("log", logBlock)
}

private fun configureRouting(
    root: JSONObject,
    inboundTag: String,
    outboundTag: String,
    network: String = "tcp,udp"
) {
    val routing = JSONObject().apply {
        put("domainStrategy", "AsIs")
        val rulesArray = JSONArray()
        val mainRule = JSONObject().apply {
            put("network", network)
            put("inboundTag", JSONArray().put(inboundTag))
            put("outboundTag", outboundTag) 
        }
        rulesArray.put(mainRule)
        put("rules", rulesArray)
    }

    root.put("routing", routing)
}

private fun configureOutbounds(
    root: JSONObject,
    outboundTag: String,
    sendThrough: String,
    address: String? = null
) {
    val outboundsArray = root.getJSONArray("outbounds")
    if (outboundsArray.length() > 0) {
        val outbound = outboundsArray.getJSONObject(0)
        outbound.put("sendThrough", sendThrough)
        outbound.put("tag", outboundTag)

        val streamSettings = outbound.getJSONObject("streamSettings")
        val realitySettings = streamSettings.getJSONObject("realitySettings")

        sanitizeOutboundRealitySettings(realitySettings)

        val shortId = realitySettings.getString("shortId")

        if(address != null) {
            val settings = outbound.getJSONObject("settings")
            settings.put("address", address)
            outbound.put("settings", settings)
        }
        
        streamSettings.put("realitySettings", realitySettings)
        outbound.put("streamSettings", streamSettings)
    }

    root.put("outbounds", outboundsArray)
}

private fun sanitizeOutboundRealitySettings(realitySettings: JSONObject) {
    realitySettings.apply {
        remove("target")
        remove("dest")
        remove("type")
        remove("xver")
        remove("mldsa65Seed")
        remove("privateKey")
        remove("mldsa65Verify")
        remove("spiderX")
        remove("minClientVer")
        remove("maxClientVer")
        remove("maxTimeDiff")
        remove("masterKeyLog")
        remove("serverNames")
        remove("shortIds")
    }
}