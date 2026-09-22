package net.libxray.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import expo.modules.kotlin.types.Enumerable
import kotlin.collections.HashMap

@Serializable
enum class JsTimeUnit(val value: String) : Enumerable {
  NANOSECONDS("NANOSECONDS"),
  MICROSECONDS("MICROSECONDS"),
  MILLISECONDS("MILLISECONDS"),
  SECONDS("SECONDS"),
  MINUTES("MINUTES"),
  HOURS("HOURS"),
  DAYS("DAYS")
}

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

    @SerialName("pingBatch")
    PING_BATCH,

    @SerialName("getXrayState")
    GET_XRAY_STATE,

    @SerialName("xrayVersion")
    VERSION
}

@Serializable
data class InvokeRequest<T> (
    val apiVersion: Int = 2,
    val method: XrayMethod,
    val payload: T
)

@Serializable
data class InvokeResponse<T> (
    val success: Boolean,
    val error: String,
    val data: T?
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
data class RunXrayInvokeRequest(
    val xrayJson: String
)

data class RunXrayRequest(
    @Field
    val xrayJson: String,
    @Field
    val appsSplitTunneling: Array<String>?,
    @Field
    val notificationErrorLocalized: String?,
    @Field
    val vpnServiceErrorLocalized: String?,
    @Field
    val vpnServiceNotificationTitle: String?,
    @Field
    val vpnServiceNotificationContent: String?,
    @Field
    val vpnServiceNotificationStatuses: HashMap<String, String>?
) : Record

@Serializable
data class RunXrayResponse(
    @Field
    val success: Boolean,
    @Field
    val error: String?
) : Record

@Serializable
data class TestXrayRequest(
    val xrayJson: String
)

@Serializable
data class TestXrayResponse(
    @Field
    val success: Boolean,
    @Field
    val data: String?,
    @Field
    val error: String?
) : Record

@Serializable
data class PingBatchRequest(
    @Field
    val configs: List<PingBatchItem>,
    @Field
    val timeout: Int,
    @Field
    val url: String?
) : Record 

@Serializable
data class PingBatchItem(
    @Field
    val xrayJson: String,
    @Field
    val outboundTag: String?
) : Record 

@Serializable
data class PingBatchResponse(
    @Field
    val results: List<PingBatchItemResponse>?
) : Record

@Serializable
data class PingBatchItemResponse(
    @Field
    val success: Boolean,
    @Field
    val delay: Long?,
    @Field
    val error: String?
) : Record

@Serializable
data class XrayStateResponse(
    val running: Boolean
)