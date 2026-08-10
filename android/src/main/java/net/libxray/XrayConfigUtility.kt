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
    sendThrough: String = "0.0.0.0",
    logLevel: String = "none"
): String {
    val root = JSONObject(initialConf)
    
    configureLogging(root, context, logLevel)

    configureEnv(root, context.filesDir.absolutePath)

    configureDNS(root)

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
    outboundAddress: String? = null,
    logLevel: String = "none"
): String {
    val root = JSONObject(initialConf)
    
    configureLogging(root, context, logLevel)

    configureEnv(root, context.filesDir.absolutePath)

    configureDNS(root)

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

    configureRouting(
        root, 
        inboundTag, 
        outboundTag
    )

    return root.toString()
}

private fun configureEnv(
    root: JSONObject,
    assetsDir: String
) {
     val env = if (root.has("env") && !root.isNull("env")) {
        root.getJSONObject("env")
    } else {
        JSONObject()
    }

    env.put("v2ray.location.asset", assetsDir)
    env.put("xray.location.asset", assetsDir)
    
    root.put("env", env)
}

private fun configureDNS(
    root: JSONObject
) {
    val dnsBlock = JSONObject().apply {
        put("hosts", JSONObject().apply{
            put("domain-!ru", "8.8.8.8")
        })
        put("servers", JSONArray().apply{
            put("8.8.8.8")
            put("1.1.1.1")
        })
    }

    root.put("dns", dnsBlock)
}

private fun configureLogging(
    root: JSONObject,
    context: Context,
    logLevel: String = "warning"
) {
    val logBlock = JSONObject().apply {
        put("loglevel", logLevel)
        put("error", "${context.filesDir.absolutePath}/xray.log")
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
        put("domainStrategy", "IPIfNonMatch")
        val rulesArray = JSONArray()
        val foreignRule = JSONObject().apply {
            put("network", network)
            put("inboundTag", JSONArray().put(inboundTag))
            put("outboundTag", outboundTag) 
        }
        val domesticRule = JSONObject().apply {
            put("domain", JSONArray().put("geosite:ru-available-only-inside"))
            put("outboundTag", "direct")
        }
        val adRule = JSONObject().apply {
            put("domain", JSONArray().put("geosite:category-ads-all"))
            put("outboundTag", "block")
        }
        rulesArray.apply {
            put(adRule)
            put(domesticRule)
            put(foreignRule)
        }
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
        val proxyOutbound = outboundsArray.getJSONObject(0)
        proxyOutbound.put("sendThrough", sendThrough)
        proxyOutbound.put("tag", outboundTag)

        val streamSettings = proxyOutbound.getJSONObject("streamSettings")
        val realitySettings = streamSettings.getJSONObject("realitySettings")

        sanitizeOutboundRealitySettings(realitySettings)

        val shortId = realitySettings.getString("shortId")

        if(address != null) {
            val settings = proxyOutbound.getJSONObject("settings")
            settings.put("address", address)
            proxyOutbound.put("settings", settings)
        }
        
        streamSettings.put("realitySettings", realitySettings)
        proxyOutbound.put("streamSettings", streamSettings)

        outboundsArray.put(
            JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
            }
        )
        
        outboundsArray.put(
            JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
            }
        )
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