package com.example.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.util.Log
import com.example.data.SessionSummary
import com.example.data.VpnConnectionState
import com.example.data.VpnServer
import com.example.data.SpeedTelemetry
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

data class ParsedWireGuardConf(
    val endpointHost: String,
    val endpointPort: Long,
    val serverPublicKey: String,
    val clientPrivateKey: String,
    val clientAddressCidr: String,
    val dns: String,
    val allowedIps: String,
    val mtu: Long
)

class WireGuardVpnManager(
    context: Context,
    private val keyStore: WireGuardKeyStore,
    private val vpsClient: VpsProvisioningClient
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val backend: GoBackend by lazy { GoBackend(appContext) }
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _connectionState = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
    val connectionState: StateFlow<VpnConnectionState> = _connectionState.asStateFlow()

    private val _telemetry = MutableStateFlow(SpeedTelemetry())
    val telemetry: StateFlow<SpeedTelemetry> = _telemetry.asStateFlow()

    private var activeServer: VpnServer? = null
    private var activeCustomPrivateKey: String? = null
    private var activeIdTokenProvider: (suspend () -> String?)? = null
    private var activeUserId: String? = null
    private var activeConfig: Config? = null

    private var userInitiatedDisconnect = false
    private var autoReconnectEnabled = true
    private var reconnectJob: Job? = null
    private var telemetryJob: Job? = null
    private var connectedStartedAtMillis: Long = 0L
    private var networkCallbackRegistered = false

    private val tunnel = FastXVpnTunnel("fastxvpn0") { newState ->
        if (newState == Tunnel.State.DOWN && !userInitiatedDisconnect) {
            val server = activeServer
            if (autoReconnectEnabled && server != null) {
                scheduleAutoReconnect(server)
            } else {
                _connectionState.value = VpnConnectionState.Disconnected
                stopTelemetry()
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val state = _connectionState.value
            val server = activeServer ?: return
            if (!userInitiatedDisconnect && autoReconnectEnabled &&
                (state is VpnConnectionState.Reconnecting || state is VpnConnectionState.Error)
            ) {
                scheduleAutoReconnect(server, immediate = true)
            }
        }

        override fun onLost(network: Network) {
            val state = _connectionState.value
            val server = activeServer ?: return
            if (!userInitiatedDisconnect && autoReconnectEnabled && state is VpnConnectionState.Connected) {
                Log.w(TAG, "Underlying network lost; monitoring WireGuard tunnel for auto-reconnect")
                scope.launch {
                    delay(1200)
                    val currentBackendState = runCatching {
                        withContext(Dispatchers.IO) { backend.getState(tunnel) }
                    }.getOrNull()
                    if (currentBackendState != Tunnel.State.UP && !userInitiatedDisconnect && autoReconnectEnabled) {
                        scheduleAutoReconnect(server)
                    }
                }
            }
        }
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        autoReconnectEnabled = enabled
        if (!enabled) {
            reconnectJob?.cancel()
            if (_connectionState.value is VpnConnectionState.Reconnecting) {
                _connectionState.value = VpnConnectionState.Disconnected
            }
        }
    }

    fun prepareVpnPermission(): Intent? {
        return VpnService.prepare(appContext)
    }

    suspend fun connect(
        server: VpnServer,
        userId: String,
        customPrivateKey: String? = null,
        idTokenProvider: suspend () -> String?
    ): Result<Unit> {
        reconnectJob?.cancel()
        userInitiatedDisconnect = false
        activeServer = server
        activeCustomPrivateKey = customPrivateKey
        activeIdTokenProvider = idTokenProvider
        activeUserId = userId

        if (VpnService.prepare(appContext) != null) {
            val msg = "Android VPN permission is required before starting WireGuard."
            _connectionState.value = VpnConnectionState.Error(msg, server)
            return Result.failure(IllegalStateException(msg))
        }

        return establishTunnel(server, userId, customPrivateKey, idTokenProvider)
    }

    private suspend fun establishTunnel(
        server: VpnServer,
        userId: String,
        customPrivateKey: String?,
        idTokenProvider: suspend () -> String?
    ): Result<Unit> {
        return try {
            _connectionState.value = VpnConnectionState.Connecting(server, "Preparing Curve25519 keys…")
            val deviceKeyPair = keyStore.getOrCreateDeviceKeyPair()
            val privateKeyBase64 = customPrivateKey?.takeIf { it.isNotBlank() }
                ?: deviceKeyPair.privateKey.toBase64()
            val publicKeyBase64 = if (!customPrivateKey.isNullOrBlank()) {
                com.wireguard.crypto.KeyPair(com.wireguard.crypto.Key.fromBase64(customPrivateKey)).publicKey.toBase64()
            } else {
                deviceKeyPair.publicKey.toBase64()
            }

            var resolvedHost = server.endpointHost.trim()
            var resolvedPort = server.endpointPort
            var resolvedServerPubKey = server.serverPublicKey.trim()
            var resolvedAddress = server.clientAddressCidr.trim()
            var resolvedDns = server.dns.trim()
            var resolvedAllowedIps = server.allowedIps.trim()
            var resolvedMtu = server.mtu.toInt()
            var resolvedKeepalive = server.persistentKeepalive.toInt()

            if (server.provisionApiUrl.isNotBlank()) {
                _connectionState.value = VpnConnectionState.Connecting(server, "Registering peer on VPS…")
                val token = idTokenProvider()
                    ?: throw IllegalStateException("Authentication token unavailable for VPS registration.")
                val provisioned = vpsClient.registerPeer(
                    provisionBaseUrl = server.provisionApiUrl,
                    firebaseIdToken = token,
                    clientPublicKey = publicKeyBase64,
                    userId = userId,
                    serverId = server.id
                )
                resolvedAddress = provisioned.clientAddressCidr
                resolvedServerPubKey = provisioned.serverPublicKey
                resolvedHost = provisioned.endpointHost ?: resolvedHost
                resolvedPort = provisioned.endpointPort ?: resolvedPort
                resolvedDns = provisioned.dns ?: resolvedDns
                resolvedAllowedIps = provisioned.allowedIps ?: resolvedAllowedIps
                resolvedMtu = provisioned.mtu?.toInt() ?: resolvedMtu
                resolvedKeepalive = provisioned.persistentKeepalive?.toInt() ?: resolvedKeepalive
            }

            require(resolvedHost.isNotBlank()) {
                "Server endpoint host is missing. Configure it in Admin Panel or Custom Config."
            }
            require(resolvedServerPubKey.length >= 32) {
                "WireGuard server public key is not configured for ${server.name}."
            }

            _connectionState.value = VpnConnectionState.Connecting(server, "Starting WireGuard GoBackend…")

            val wgConfig = withContext(Dispatchers.IO) {
                val iface = Interface.Builder()
                    .parsePrivateKey(privateKeyBase64)
                    .parseAddresses(resolvedAddress)
                    .parseDnsServers(resolvedDns)
                    .setMtu(resolvedMtu.coerceIn(1200, 1500))
                    .build()

                val peerBuilder = Peer.Builder()
                    .parsePublicKey(resolvedServerPubKey)
                    .parseEndpoint("$resolvedHost:$resolvedPort")
                    .parseAllowedIPs(resolvedAllowedIps)
                if (resolvedKeepalive > 0) {
                    peerBuilder.setPersistentKeepalive(resolvedKeepalive)
                }

                Config.Builder()
                    .setInterface(iface)
                    .addPeer(peerBuilder.build())
                    .build()
            }

            activeConfig = wgConfig

            val finalState = withContext(Dispatchers.IO) {
                backend.setState(tunnel, Tunnel.State.UP, wgConfig)
            }

            if (finalState == Tunnel.State.UP) {
                registerNetworkCallbackIfNeeded()
                connectedStartedAtMillis = System.currentTimeMillis()
                _connectionState.value = VpnConnectionState.Connected(server)
                startTelemetryLoop()
                Result.success(Unit)
            } else {
                val err = "WireGuard GoBackend returned state: $finalState"
                _connectionState.value = VpnConnectionState.Error(err, server)
                Result.failure(IllegalStateException(err))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "WireGuard connect failed", e)
            val message = e.localizedMessage ?: "Failed to establish WireGuard tunnel."
            _connectionState.value = VpnConnectionState.Error(message, server)
            Result.failure(e)
        }
    }

    suspend fun disconnect(): SessionSummary? {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        stopTelemetry()
        unregisterNetworkCallback()

        val lastServer = activeServer
        val currentTelemetry = _telemetry.value

        withContext(Dispatchers.IO) {
            try {
                if (backend.getState(tunnel) != Tunnel.State.DOWN) {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping GoBackend tunnel: ${e.message}")
            }
        }

        _connectionState.value = VpnConnectionState.Disconnected
        _telemetry.value = SpeedTelemetry()

        return if (lastServer != null && currentTelemetry.durationSeconds > 0L) {
            SessionSummary(
                server = lastServer,
                bytesDown = currentTelemetry.totalRxBytes,
                bytesUp = currentTelemetry.totalTxBytes,
                durationSeconds = currentTelemetry.durationSeconds
            )
        } else {
            null
        }
    }

    private fun scheduleAutoReconnect(server: VpnServer, immediate: Boolean = false) {
        if (!autoReconnectEnabled || userInitiatedDisconnect) return
        if (reconnectJob?.isActive == true) return
        val userId = activeUserId ?: return
        val tokenProvider = activeIdTokenProvider ?: return

        reconnectJob = scope.launch {
            var attempt = 1
            while (isActive && autoReconnectEnabled && !userInitiatedDisconnect && attempt <= 5) {
                _connectionState.value = VpnConnectionState.Reconnecting(server, attempt)
                if (!immediate || attempt > 1) {
                    val backoffMs = (2000L * attempt).coerceAtMost(10000L)
                    delay(backoffMs)
                }
                if (VpnService.prepare(appContext) != null) {
                    _connectionState.value = VpnConnectionState.Error("VPN permission revoked", server)
                    break
                }
                val result = establishTunnel(server, userId, activeCustomPrivateKey, tokenProvider)
                if (result.isSuccess) {
                    break
                }
                attempt++
            }
        }
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            var prevRx = 0L
            var prevTx = 0L
            val downHistory = ArrayDeque<Long>(30)
            val upHistory = ArrayDeque<Long>(30)

            while (isActive) {
                val stats = withContext(Dispatchers.IO) {
                    runCatching { backend.getStatistics(tunnel) }.getOrNull()
                }
                val state = withContext(Dispatchers.IO) {
                    runCatching { backend.getState(tunnel) }.getOrNull()
                }

                if (state != Tunnel.State.UP && !userInitiatedDisconnect) {
                    val server = activeServer
                    if (autoReconnectEnabled && server != null) {
                        scheduleAutoReconnect(server)
                    } else {
                        _connectionState.value = VpnConnectionState.Disconnected
                    }
                    break
                }

                if (stats != null) {
                    val rx = stats.totalRx()
                    val tx = stats.totalTx()
                    val deltaRx = if (prevRx > 0L) (rx - prevRx).coerceAtLeast(0L) else 0L
                    val deltaTx = if (prevTx > 0L) (tx - prevTx).coerceAtLeast(0L) else 0L
                    prevRx = rx
                    prevTx = tx

                    if (downHistory.size >= 24) downHistory.removeFirst()
                    if (upHistory.size >= 24) upHistory.removeFirst()
                    downHistory.addLast(deltaRx)
                    upHistory.addLast(deltaTx)

                    // WireGuard Noise_IKpsk2 only decrypts & delivers RX bytes after a valid peer handshake
                    val latestHandshake = if (rx > 0L) System.currentTimeMillis() else 0L

                    val elapsedSec = ((System.currentTimeMillis() - connectedStartedAtMillis) / 1000L)
                        .coerceAtLeast(0L)

                    _telemetry.value = SpeedTelemetry(
                        downloadBytesPerSec = deltaRx,
                        uploadBytesPerSec = deltaTx,
                        totalRxBytes = rx,
                        totalTxBytes = tx,
                        durationSeconds = elapsedSec,
                        latestHandshakeEpochMillis = latestHandshake,
                        downloadHistoryBps = downHistory.toList(),
                        uploadHistoryBps = upHistory.toList()
                    )
                }
                delay(1000L)
            }
        }
    }

    private fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
    }

    private fun registerNetworkCallbackIfNeeded() {
        if (networkCallbackRegistered) return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register NetworkCallback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        } finally {
            networkCallbackRegistered = false
        }
    }

    companion object {
        private const val TAG = "WireGuardVpnManager"

        fun parseWireGuardConf(rawText: String): Result<ParsedWireGuardConf> {
            return try {
                val parsed = Config.parse(ByteArrayInputStream(rawText.trim().toByteArray(Charsets.UTF_8)))
                val iface = parsed.`interface`
                val peer = parsed.peers.firstOrNull()
                    ?: return Result.failure(IllegalArgumentException("WireGuard config must contain a [Peer] section."))

                val endpoint = peer.endpoint.orElse(null)
                    ?: return Result.failure(IllegalArgumentException("[Peer] must contain an Endpoint=host:port."))

                val address = iface.addresses.firstOrNull()?.toString() ?: "10.66.66.2/32"
                val dns = iface.dnsServers.joinToString(", ") { it.hostAddress ?: "" }
                    .ifBlank { "1.1.1.1, 1.0.0.1" }
                val allowedIps = peer.allowedIps.joinToString(", ") { it.toString() }
                    .ifBlank { "0.0.0.0/0, ::/0" }

                Result.success(
                    ParsedWireGuardConf(
                        endpointHost = endpoint.host,
                        endpointPort = endpoint.port.toLong(),
                        serverPublicKey = peer.publicKey.toBase64(),
                        clientPrivateKey = iface.keyPair.privateKey.toBase64(),
                        clientAddressCidr = address,
                        dns = dns,
                        allowedIps = allowedIps,
                        mtu = iface.mtu.orElse(1280).toLong()
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
