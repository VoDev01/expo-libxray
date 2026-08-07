package net.libxray

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class XrayMethod {
    @SerialName("convertShareLinksToXrayJson")
    CONVERT_SHARE_LINKS_TO_JSON,

    @SerialName("convertXrayJsonToShareLinks")
    CONVERT_JSON_TO_SHARE_LINKS,

    @SerialName("runXray")
    RUN_XRAY,

    @SerialName("stopXray")
    STOP_XRAY,

    @SerialName("testXray")
    TEST_XRAY,

    @SerialName("ping")
    PING,

    @SerialName("getXrayState")
    GET_XRAY_STATE
}

@Serializable
data class InvokeRequest<T> (
    val apiVersion: Int = 2,
    val method: XrayMethod,
    val payload: T
)

@Serializable
data class ConvertLinksRequest(
    val text: String
)

@Serializable
data class ConvertXrayJsonRequest(
    val xrayJson: String
)

@Serializable
data class ConvertXrayJsonResponse(
    val links: String
)

@Serializable
data class InvokeResponse(
    val success: Boolean,
    val error: String? = null,
    val data: String? = null
)

@Serializable
data class RunXrayRequest(
    val xrayJson: String
)

@Serializable
data class TestXrayRequest(
    val xrayJson: String
)

@Serializable
data class PingRequest(
    val configPath: String? = null,
    val timeout: Int = 5,
    val url: String? = null,
    val proxy: String? = null
)

@Serializable
data class PingResponse(
    val delay: Long? = null
)

@Serializable
data class XrayStateResponse(
    val running: Boolean
)