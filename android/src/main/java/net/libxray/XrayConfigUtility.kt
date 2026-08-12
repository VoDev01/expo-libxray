package net.libxray

import org.json.JSONArray
import org.json.JSONObject

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