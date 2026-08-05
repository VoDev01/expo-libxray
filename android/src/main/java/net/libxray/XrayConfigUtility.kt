package net.libxray

import org.json.JSONArray
import org.json.JSONObject
import android.content.Context

fun addSocksInboundToClientXrayConfig(
    context: Context,
    initialConf: String,
    listenIp: String = "127.0.0.1",
    serverPort: Int = 10808,
    inboundTag: String = "SOCKS LOCAL",
    outboundTag: String = "VLESS TCP REALITY"
): String {
    val root = JSONObject(initialConf)
    val logBlock = JSONObject().apply {
        put("loglevel", "debug")
        put("error", "${context.filesDir.absolutePath}/xray_error.log")
    }

    root.put("log", logBlock)

    val inboundsArray = JSONArray()
    val socksInbound = JSONObject().apply {
        put("tag", inboundTag)
        put("listen", listenIp)
        put("port", serverPort)
        put("protocol", "dokodemo-door")
        val inSettings = JSONObject().apply {
            put("network", "tcp,udp") 
            put("followRedirect", true)
            put("address", "")
            put("userLevel", 0)
        }
        put("settings", inSettings)
    }

    inboundsArray.put(socksInbound)

    root.put("inbounds", inboundsArray)

    val outboundsArray = root.getJSONArray("outbounds")
    if (outboundsArray.length() > 0) {
        val outbound = outboundsArray.getJSONObject(0)
        outbound.put("sendThrough", "10.0.0.2")
        outbound.put("tag", outboundTag)

        val streamSettings = outbound.getJSONObject("streamSettings")
        val realitySettings = streamSettings.getJSONObject("realitySettings")
            
        val serverNamesArray = JSONArray().apply {
            put("microsoft.com")
            put("google.com")
            put("wikipedia.org")
        }

        if (realitySettings.has("privateKey")) {
            val pubKey = realitySettings.optString("publicKey", "")
            if (pubKey.isNotEmpty()) {
                realitySettings.put("privateKey", pubKey)
            }
        } else {
            val pubKey = realitySettings.optString("publicKey", "")
            realitySettings.put("privateKey", pubKey)
        }

        val shortId = realitySettings.getString("shortId")
        val shortIds = JSONArray().put(shortId)
        
        realitySettings.put("shortIds", shortIds)
        realitySettings.put("serverNames", serverNamesArray)
        streamSettings.put("realitySettings", realitySettings)
        outbound.put("streamSettings", streamSettings)
    }

    root.put("outbounds", outboundsArray)

    val routing = JSONObject().apply {
        put("domainStrategy", "AsIs")
        val rulesArray = JSONArray()
        val mainRule = JSONObject().apply {
            put("type", "field")
            put("network", "tcp,udp")
            put("inboundTag", JSONArray().put(inboundTag))
            put("outboundTag", outboundTag) 
        }
        rulesArray.put(mainRule)
        put("rules", rulesArray)
    }

    return root.toString()
}
