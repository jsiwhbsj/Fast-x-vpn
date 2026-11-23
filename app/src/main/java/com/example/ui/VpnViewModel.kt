package com.example.ui

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CustomWireGuardConfig
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.VpnConnectionState
import com.example.data.VpnRepository
import com.example.data.VpnServer
import com.example.data.VpnSession
import com.example.data.SpeedTelemetry
import com.example.vpn.VpsHealthResponse
import com.example.vpn.VpsProvisioningClient
import com.example.vpn.WireGuardKeyStore
import com.example.vpn.WireGuardVpnManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VpnViewModel(
    private val repository: VpnRepository,
    private val vpnManager: WireGuardVpnManager,
    private val keyStore: WireGuardKeyStore,
    private val vpsClient: VpsProvisioningClient,
    val currentUserId: String
) : ViewModel() {

    val connectionState: StateFlow<VpnConnectionState> = vpnManager.connectionState
    val telemetry: StateFlow<SpeedTelemetry> = vpnManager.telemetry

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _devicePublicKey = MutableStateFlow("")
    val devicePublicKey: StateFlow<String> = _devicePublicKey.asStateFlow()

    private val _selectedServer = MutableStateFlow<VpnServer?>(null)
    val selectedServer: StateFlow<VpnServer?> = _selectedServer.asStateFlow()

    private val _measuredLatencies = MutableStateFlow<Map<String, Long>>(emptyMap())
    val measuredLatencies: StateFlow<Map<String, Long>> = _measuredLatencies.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _vpsHealthMap = MutableStateFlow<Map<String, Result<VpsHealthResponse>>>(emptyMap())
    val vpsHealthMap: StateFlow<Map<String, Result<VpsHealthResponse>>> = _vpsHealthMap.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _autoReconnect = MutableStateFlow(true)
    val autoReconnect: StateFlow<Boolean> = _autoReconnect.asStateFlow()

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFlowError(error, "Failed to load profile")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val activeServersState: StateFlow<UiState<List<VpnServer>>> = repository.observeActiveServers()
        .map<List<VpnServer>, UiState<List<VpnServer>>> { list ->
            val savedId = keyStore.selectedServerIdFlow.first()
            if (_selectedServer.value == null && list.isNotEmpty()) {
                _selectedServer.value = list.firstOrNull { it.id == savedId } ?: list.first()
            }
            UiState.Success(list)
        }
        .catch { error -> emit(unwrapFlowError(error, "Failed to load servers")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val sessionsState: StateFlow<UiState<List<VpnSession>>> = repository.observeSessions(currentUserId)
        .map<List<VpnSession>, UiState<List<VpnSession>>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFlowError(error, "Failed to load session history")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val customConfigsState: StateFlow<UiState<List<CustomWireGuardConfig>>> =
        repository.observeCustomConfigs(currentUserId)
            .map<List<CustomWireGuardConfig>, UiState<List<CustomWireGuardConfig>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFlowError(error, "Failed to load custom configs")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val _adminServersState = MutableStateFlow<UiState<List<VpnServer>>>(UiState.Loading)
    val adminServersState: StateFlow<UiState<List<VpnServer>>> = _adminServersState.asStateFlow()

    private val _adminUsersState = MutableStateFlow<UiState<List<UserProfile>>>(UiState.Loading)
    val adminUsersState: StateFlow<UiState<List<UserProfile>>> = _adminUsersState.asStateFlow()

    init {
        initializeUserAndAdmin()
    }

    private fun initializeUserAndAdmin() {
        viewModelScope.launch {
            val pubKey = keyStore.getDevicePublicKey()
            _devicePublicKey.value = pubKey
            val autoRec = keyStore.autoReconnectFlow.first()
            _autoReconnect.value = autoRec
            vpnManager.setAutoReconnectEnabled(autoRec)

            Firebase.auth.currentUser?.let { user ->
                repository.ensureUserProfile(user, pubKey)
            }

            val admin = repository.isCurrentUserAdmin()
            _isAdmin.value = admin
            if (admin) {
                observeAdminStreams()
            }
        }
    }

    private fun observeAdminStreams() {
        viewModelScope.launch {
            repository.observeAllServersForAdmin()
                .map<List<VpnServer>, UiState<List<VpnServer>>> { UiState.Success(it) }
                .catch { error -> emit(unwrapFlowError(error, "Failed to load admin servers")) }
                .collect { _adminServersState.value = it }
        }
        viewModelScope.launch {
            repository.observeAllUsersForAdmin()
                .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
                .catch { error -> emit(unwrapFlowError(error, "Failed to load admin users")) }
                .collect { _adminUsersState.value = it }
        }
    }

    fun prepareVpnPermission(): Intent? = vpnManager.prepareVpnPermission()

    fun selectServer(server: VpnServer) {
        _selectedServer.value = server
        viewModelScope.launch {
            keyStore.setSelectedServerId(server.id)
        }
    }

    fun toggleAutoReconnect(enabled: Boolean) {
        _autoReconnect.value = enabled
        vpnManager.setAutoReconnectEnabled(enabled)
        viewModelScope.launch {
            keyStore.setAutoReconnect(enabled)
            repository.setAutoReconnect(currentUserId, enabled)
        }
    }

    fun connectSelectedServer() {
        val server = _selectedServer.value ?: run {
            _statusBanner.value = "Select a VPN server node or add a custom WireGuard config first."
            return
        }
        if (!server.isWireGuardReady) {
            _statusBanner.value = "Node '${server.name}' needs a WireGuard server public key or VPS provisioning URL (configure in Admin Panel)."
            return
        }
        viewModelScope.launch {
            val customPrivateKey = if (server.id.startsWith("custom_")) {
                keyStore.getCustomPrivateKey(server.id.removePrefix("custom_"))
            } else null

            val result = vpnManager.connect(
                server = server,
                userId = currentUserId,
                customPrivateKey = customPrivateKey,
                idTokenProvider = { repository.getFirebaseIdToken() }
            )
            result.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Connection failed"
            }
        }
    }

    fun disconnectVpn() {
        viewModelScope.launch {
            val summary = vpnManager.disconnect()
            if (summary != null) {
                repository.recordSession(currentUserId, summary)
            }
        }
    }

    fun pingServers(servers: List<VpnServer>) {
        if (_isPinging.value || servers.isEmpty()) return
        viewModelScope.launch {
            _isPinging.value = true
            try {
                val results = servers.map { server ->
                    async {
                        val ms = vpsClient.measureEndpointLatencyMs(
                            server.endpointHost,
                            server.endpointPort.toInt()
                        )
                        server.id to ms
                    }
                }.awaitAll().toMap()
                _measuredLatencies.value = results
            } finally {
                _isPinging.value = false
            }
        }
    }

    fun importCustomConfig(
        name: String,
        rawConfText: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val parsed = WireGuardVpnManager.parseWireGuardConf(rawConfText).getOrElse { err ->
                onComplete(Result.failure(err))
                return@launch
            }
            val cfg = CustomWireGuardConfig(
                userId = currentUserId,
                name = name.ifBlank { parsed.endpointHost },
                endpointHost = parsed.endpointHost,
                endpointPort = parsed.endpointPort,
                serverPublicKey = parsed.serverPublicKey,
                clientAddressCidr = parsed.clientAddressCidr,
                dns = parsed.dns,
                allowedIps = parsed.allowedIps,
                mtu = parsed.mtu
            )
            val saveRes = repository.saveCustomConfig(currentUserId, cfg)
            saveRes.onSuccess { docId ->
                keyStore.saveCustomPrivateKey(docId, parsed.clientPrivateKey)
                val asServer = cfg.copy(id = docId).toVpnServer()
                _selectedServer.value = asServer
                _statusBanner.value = "Imported WireGuard config '${asServer.name}'"
                onComplete(Result.success(Unit))
            }.onFailure { err ->
                onComplete(Result.failure(err))
            }
        }
    }

    fun saveManualCustomConfig(
        name: String,
        endpointHost: String,
        endpointPort: Long,
        serverPublicKey: String,
        clientPrivateKey: String,
        clientAddressCidr: String,
        dns: String,
        allowedIps: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val cfg = CustomWireGuardConfig(
                userId = currentUserId,
                name = name,
                endpointHost = endpointHost,
                endpointPort = endpointPort,
                serverPublicKey = serverPublicKey,
                clientAddressCidr = clientAddressCidr,
                dns = dns,
                allowedIps = allowedIps,
                mtu = 1280L
            )
            val res = repository.saveCustomConfig(currentUserId, cfg)
            res.onSuccess { docId ->
                if (clientPrivateKey.isNotBlank()) {
                    keyStore.saveCustomPrivateKey(docId, clientPrivateKey)
                }
                val asServer = cfg.copy(id = docId).toVpnServer()
                _selectedServer.value = asServer
                _statusBanner.value = "Saved custom WireGuard node '${asServer.name}'"
                onComplete(Result.success(Unit))
            }.onFailure { err ->
                onComplete(Result.failure(err))
            }
        }
    }

    fun deleteCustomConfig(configId: String) {
        viewModelScope.launch {
            repository.deleteCustomConfig(currentUserId, configId)
            keyStore.removeCustomPrivateKey(configId)
            if (_selectedServer.value?.id == "custom_$configId") {
                _selectedServer.value = null
            }
        }
    }

    // --- Admin Actions ---

    fun seedDefaultSixRegions() {
        viewModelScope.launch {
            val res = repository.seedDefaultRegionNodes()
            _statusBanner.value = if (res.isSuccess) {
                "Initialized Bangladesh, USA, Singapore, UK, Canada & India nodes in Firestore."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to initialize region nodes."
            }
        }
    }

    fun saveAdminServer(server: VpnServer, isNew: Boolean, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.saveAdminServer(server, isNew)
            if (res.isSuccess) {
                _statusBanner.value = if (isNew) "Added server '${server.name}'" else "Updated '${server.name}'"
            }
            onDone(res)
        }
    }

    fun toggleServerActive(serverId: String, active: Boolean) {
        viewModelScope.launch {
            repository.toggleServerActive(serverId, active).onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to update server status"
            }
        }
    }

    fun deleteAdminServer(serverId: String) {
        viewModelScope.launch {
            repository.deleteServer(serverId).onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to delete server"
            }
        }
    }

    fun checkVpsHealth(server: VpnServer) {
        if (server.provisionApiUrl.isBlank()) {
            _statusBanner.value = "No VPS Provisioning API URL set on ${server.name}."
            return
        }
        viewModelScope.launch {
            val res = vpsClient.checkHealth(server.provisionApiUrl)
            _vpsHealthMap.value = _vpsHealthMap.value + (server.id to res)
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun notifyMessage(msg: String) {
        _statusBanner.value = msg
    }

    private fun unwrapFlowError(error: Throwable, fallback: String): UiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, fallback, root)
        return UiState.Error(root.localizedMessage ?: fallback)
    }

    companion object {
        private const val TAG = "VpnViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
