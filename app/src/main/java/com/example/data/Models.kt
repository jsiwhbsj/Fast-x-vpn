package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class VpnServer(
    @DocumentId val id: String = "",
    val name: String = "",
    val country: String = "",
    val countryCode: String = "",
    val city: String = "",
    val flagEmoji: String = "",
    val endpointHost: String = "",
    val endpointPort: Long = 51820L,
    val serverPublicKey: String = "",
    val dns: String = "1.1.1.1, 1.0.0.1",
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val clientAddressCidr: String = "10.66.66.2/32",
    val mtu: Long = 1280L,
    val persistentKeepalive: Long = 25L,
    val provisionApiUrl: String = "",
    val active: Boolean = true,
    val latencyMs: Long = 0L,
    val loadPercent: Long = 15L,
    val bandwidthMbps: Long = 1000L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "name" to name.trim().take(80),
            "country" to country.trim().take(60),
            "countryCode" to countryCode.trim().uppercase().take(4),
            "city" to city.trim().take(60),
            "flagEmoji" to flagEmoji.trim().take(16),
            "endpointHost" to endpointHost.trim().take(253),
            "endpointPort" to endpointPort.coerceIn(1L, 65535L),
            "serverPublicKey" to serverPublicKey.trim().take(128),
            "dns" to dns.trim().ifBlank { "1.1.1.1, 1.0.0.1" }.take(120),
            "allowedIps" to allowedIps.trim().ifBlank { "0.0.0.0/0, ::/0" }.take(120),
            "clientAddressCidr" to clientAddressCidr.trim().ifBlank { "10.66.66.2/32" }.take(64),
            "mtu" to mtu.coerceIn(1200L, 1500L),
            "persistentKeepalive" to persistentKeepalive.coerceIn(0L, 120L),
            "active" to active,
            "latencyMs" to latencyMs.coerceIn(0L, 9999L),
            "loadPercent" to loadPercent.coerceIn(0L, 100L),
            "bandwidthMbps" to bandwidthMbps.coerceIn(0L, 100000L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (provisionApiUrl.isNotBlank()) {
            map["provisionApiUrl"] = provisionApiUrl.trim().take(300)
        }
        return map
    }

    fun toUpdateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "name" to name.trim().take(80),
            "country" to country.trim().take(60),
            "countryCode" to countryCode.trim().uppercase().take(4),
            "city" to city.trim().take(60),
            "flagEmoji" to flagEmoji.trim().take(16),
            "endpointHost" to endpointHost.trim().take(253),
            "endpointPort" to endpointPort.coerceIn(1L, 65535L),
            "serverPublicKey" to serverPublicKey.trim().take(128),
            "dns" to dns.trim().ifBlank { "1.1.1.1, 1.0.0.1" }.take(120),
            "allowedIps" to allowedIps.trim().ifBlank { "0.0.0.0/0, ::/0" }.take(120),
            "clientAddressCidr" to clientAddressCidr.trim().ifBlank { "10.66.66.2/32" }.take(64),
            "mtu" to mtu.coerceIn(1200L, 1500L),
            "persistentKeepalive" to persistentKeepalive.coerceIn(0L, 120L),
            "active" to active,
            "latencyMs" to latencyMs.coerceIn(0L, 9999L),
            "loadPercent" to loadPercent.coerceIn(0L, 100L),
            "bandwidthMbps" to bandwidthMbps.coerceIn(0L, 100000L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (provisionApiUrl.isNotBlank()) {
            map["provisionApiUrl"] = provisionApiUrl.trim().take(300)
        }
        return map
    }

    val isWireGuardReady: Boolean
        get() = endpointHost.isNotBlank() &&
            endpointPort in 1L..65535L &&
            (serverPublicKey.trim().length >= 32 || provisionApiUrl.isNotBlank())
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val clientPublicKey: String = "",
    val totalBytesDown: Long = 0L,
    val totalBytesUp: Long = 0L,
    val totalSessions: Long = 0L,
    val totalConnectedSeconds: Long = 0L,
    val lastServerId: String = "",
    val lastServerName: String = "",
    val autoReconnect: Boolean = true,
    val plan: String = "FREE",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VpnSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val serverId: String = "",
    val serverName: String = "",
    val country: String = "",
    val protocol: String = "WireGuard",
    val bytesDown: Long = 0L,
    val bytesUp: Long = 0L,
    val durationSeconds: Long = 0L,
    val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CustomWireGuardConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val endpointHost: String = "",
    val endpointPort: Long = 51820L,
    val serverPublicKey: String = "",
    val clientAddressCidr: String = "10.66.66.2/32",
    val dns: String = "1.1.1.1, 1.0.0.1",
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val mtu: Long = 1280L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toVpnServer(): VpnServer = VpnServer(
        id = "custom_$id",
        name = name,
        country = "Custom Node",
        countryCode = "WG",
        city = endpointHost,
        flagEmoji = "\uD83D\uDD10",
        endpointHost = endpointHost,
        endpointPort = endpointPort,
        serverPublicKey = serverPublicKey,
        dns = dns,
        allowedIps = allowedIps,
        clientAddressCidr = clientAddressCidr,
        mtu = mtu,
        persistentKeepalive = 25L,
        provisionApiUrl = "",
        active = true
    )
}

sealed interface VpnConnectionState {
    data object Disconnected : VpnConnectionState
    data class Connecting(val server: VpnServer, val stage: String = "Negotiating WireGuard keys…") : VpnConnectionState
    data class Connected(val server: VpnServer) : VpnConnectionState
    data class Reconnecting(val server: VpnServer, val attempt: Int) : VpnConnectionState
    data class Error(val message: String, val server: VpnServer? = null) : VpnConnectionState
}

data class SpeedTelemetry(
    val downloadBytesPerSec: Long = 0L,
    val uploadBytesPerSec: Long = 0L,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val durationSeconds: Long = 0L,
    val latestHandshakeEpochMillis: Long = 0L,
    val downloadHistoryBps: List<Long> = emptyList(),
    val uploadHistoryBps: List<Long> = emptyList()
) {
    val hasActiveHandshake: Boolean
        get() = latestHandshakeEpochMillis > 0L
}

data class SessionSummary(
    val server: VpnServer,
    val bytesDown: Long,
    val bytesUp: Long,
    val durationSeconds: Long
)

object DefaultRegionTemplates {
    val sixCoreRegions = listOf(
        VpnServer(
            id = "bd_dhaka_01",
            name = "Dhaka Core 01",
            country = "Bangladesh",
            countryCode = "BD",
            city = "Dhaka",
            flagEmoji = "\uD83C\uDDE7\uD83C\uDDE9",
            endpointHost = "bd-dhk-01.fastxvpn.net",
            endpointPort = 51820L,
            serverPublicKey = "",
            dns = "1.1.1.1, 1.0.0.1",
            allowedIps = "0.0.0.0/0, ::/0",
            clientAddressCidr = "10.66.10.2/32",
            mtu = 1280L,
            persistentKeepalive = 25L,
            provisionApiUrl = "",
            active = true,
            latencyMs = 0L,
            loadPercent = 18L,
            bandwidthMbps = 1000L
        ),
        VpnServer(
            id = "us_newyork_01",
            name = "New York Edge 01",
            country = "USA",
            countryCode = "US",
            city = "New York",
            flagEmoji = "\uD83C\uDDFA\uD83C\uDDF8",
            endpointHost = "us-nyc-01.fastxvpn.net",
            endpointPort = 51820L,
            serverPublicKey = "",
            dns = "1.1.1.1, 1.0.0.1",
            allowedIps = "0.0.0.0/0, ::/0",
            clientAddressCidr = "10.66.20.2/32",
            mtu = 1280L,
            persistentKeepalive = 25L,
            provisionApiUrl = "",
            active = true,
            latencyMs = 0L,
            loadPercent = 24L,
            bandwidthMbps = 10000L
        ),
        VpnServer(
            id = "sg_singapore_01",
            name = "Singapore One 01",
            country = "Singapore",
            countryCode = "SG",
            city = "Singapore",
            flagEmoji = "\uD83C\uDDF8\uD83C\uDDEC",
            endpointHost = "sg-sin-01.fastxvpn.net",
            endpointPort = 51820L,
            serverPublicKey = "",
            dns = "1.1.1.1, 1.0.0.1",
            allowedIps = "0.0.0.0/0, ::/0",
            clientAddressCidr = "10.66.30.2/32",
            mtu = 1280L,
            persistentKeepalive = 25L,
            provisionApiUrl = "",
            active = true,
            latencyMs = 0L,
            loadPercent = 21L,
            bandwidthMbps = 10000L
        ),
        VpnServer(
            id = "uk_london_01",
            name = "London Docklands 01",
            country = "UK",
            countryCode = "GB",
            city = "London",
            flagEmoji = "\uD83C\uDDEC\uD83C\uDDE7",
            endpointHost = "uk-lon-01.fastxvpn.net",
            endpointPort = 51820L,
            serverPublicKey = "",
            dns = "1.1.1.1, 1.0.0.1",
            allowedIps = "0.0.0.0/0, ::/0",
            clientAddressCidr = "10.66.40.2/32",
            mtu = 1280L,
            persistentKeepalive = 25L,
            provisionApiUrl = "",
            active = true,
            latencyMs = 0L,
            loadPercent = 19L,
            bandwidthMbps = 10000L
        ),
        VpnServer(
            id = "ca_toronto_01",
            name = "Toronto North 01",
            country = "Canada",
            countryCode = "CA",
            city = "Toronto",
            flagEmoji = "\uD83C\uDDE8\uD83C\uDDE6",
            endpointHost = "ca-yyz-01.fastxvpn.net",
            endpointPort = 51820L,
            serverPublicKey = "",
            dns = "1.1.1.1, 1.0.0.1",
            allowedIps = "0.0.0.0/0, ::/0",
            clientAddressCidr = "10.66.50.2/32",
            mtu = 1280L,
            persistentKeepalive = 25L,
            provisionApiUrl = "",
            active = true,
            latencyMs = 0L,
            loadPercent = 14L,
            bandwidthMbps = 5000L
        ),
        VpnServer(
            id = "in_mumbai_01",
            name = "Mumbai West 01",
            country = "India",
            countryCode = "IN",
            city = "Mumbai",
            flagEmoji = "\uD83C\uDDEE\uD83C\uDDF3",
            endpointHost = "in-bom-01.fastxvpn.net",
            endpointPort = 51820L,
            serverPublicKey = "",
            dns = "1.1.1.1, 1.0.0.1",
            allowedIps = "0.0.0.0/0, ::/0",
            clientAddressCidr = "10.66.60.2/32",
            mtu = 1280L,
            persistentKeepalive = 25L,
            provisionApiUrl = "",
            active = true,
            latencyMs = 0L,
            loadPercent = 27L,
            bandwidthMbps = 5000L
        )
    )
}
