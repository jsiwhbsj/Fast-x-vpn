package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomWireGuardConfig
import com.example.data.UiState
import com.example.data.VpnServer
import com.example.ui.components.BrandPillBadge
import com.example.ui.theme.BorderLavender
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.PurpleTint
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnectedContainer
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusError
import com.example.ui.theme.SurfaceOffWhite
import com.example.ui.theme.WhitePure

@Composable
fun ServersScreen(
    serversState: UiState<List<VpnServer>>,
    customConfigsState: UiState<List<CustomWireGuardConfig>>,
    selectedServerId: String?,
    measuredLatencies: Map<String, Long>,
    isPinging: Boolean,
    isAdmin: Boolean,
    onSelectServer: (VpnServer) -> Unit,
    onPingServers: (List<VpnServer>) -> Unit,
    onImportWireGuardConf: (name: String, confText: String, onDone: (Result<Unit>) -> Unit) -> Unit,
    onSaveManualConfig: (
        name: String,
        host: String,
        port: Long,
        pubKey: String,
        privKey: String,
        cidr: String,
        dns: String,
        allowedIps: String,
        onDone: (Result<Unit>) -> Unit
    ) -> Unit,
    onDeleteCustomConfig: (String) -> Unit,
    onSeedSixRegions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showImportDialog by remember { mutableStateOf(false) }

    val globalServers = (serversState as? UiState.Success)?.data ?: emptyList()
    val customConfigs = (customConfigsState as? UiState.Success)?.data ?: emptyList()
    val customServers = remember(customConfigs) { customConfigs.map { it.toVpnServer() } }
    val allCombined = remember(globalServers, customServers) { globalServers + customServers }

    val filteredServers = remember(allCombined, searchQuery, selectedFilter) {
        allCombined.filter { server ->
            val matchesQuery = searchQuery.isBlank() ||
                server.country.contains(searchQuery, ignoreCase = true) ||
                server.city.contains(searchQuery, ignoreCase = true) ||
                server.name.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Asia" -> server.countryCode in setOf("BD", "SG", "IN")
                "Americas" -> server.countryCode in setOf("US", "CA")
                "Europe" -> server.countryCode in setOf("GB", "UK")
                "Custom" -> server.id.startsWith("custom_")
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceOffWhite),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WhitePure)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VPN Locations",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bangladesh, USA, Singapore, UK, Canada & India",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onPingServers(allCombined) },
                            enabled = !isPinging && allCombined.isNotEmpty(),
                            modifier = Modifier.testTag("ping_servers_button"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (isPinging) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.NetworkPing,
                                    contentDescription = "Measure Ping",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Ping")
                        }

                        Button(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.testTag("add_custom_config_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add WireGuard Config",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Import")
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_search_input"),
                    placeholder = { Text("Search country, city, or node…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search servers",
                            tint = PurplePrimary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Asia", "Americas", "Europe", "Custom").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PurplePrimary,
                                selectedLabelColor = WhitePure
                            )
                        )
                    }
                }
            }

            // Content List
            when (serversState) {
                UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PurplePrimary)
                    }
                }

                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = serversState.message,
                            color = StatusError,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                is UiState.Success -> {
                    if (filteredServers.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(PurpleLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = "No servers",
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No Active Server Nodes Yet",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isAdmin) {
                                    "Initialize the 6 default free regions (Bangladesh, USA, Singapore, UK, Canada, India) or import a WireGuard .conf."
                                } else {
                                    "Admins haven't published VPS nodes yet, or your filter matched none. You can import your own WireGuard .conf right now."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (isAdmin && selectedFilter == "All" && searchQuery.isBlank()) {
                                Button(
                                    onClick = onSeedSixRegions,
                                    modifier = Modifier.testTag("seed_regions_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Initialize 6 Region Nodes")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            OutlinedButton(
                                onClick = { showImportDialog = true },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Import WireGuard .conf")
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredServers, key = { it.id }) { server ->
                                val isSelected = server.id == selectedServerId
                                val pingMs = measuredLatencies[server.id]
                                ServerNodeCard(
                                    server = server,
                                    isSelected = isSelected,
                                    measuredLatencyMs = pingMs,
                                    onClick = { onSelectServer(server) },
                                    onDeleteCustom = if (server.id.startsWith("custom_")) {
                                        { onDeleteCustomConfig(server.id.removePrefix("custom_")) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ImportWireGuardDialog(
            onDismiss = { showImportDialog = false },
            onImportConf = { name, text, onErr ->
                onImportWireGuardConf(name, text) { res ->
                    if (res.isSuccess) showImportDialog = false
                    else onErr(res.exceptionOrNull()?.localizedMessage ?: "Invalid WireGuard config")
                }
            },
            onSaveManual = { name, host, port, pubKey, privKey, cidr, dns, allowedIps, onErr ->
                onSaveManualConfig(name, host, port, pubKey, privKey, cidr, dns, allowedIps) { res ->
                    if (res.isSuccess) showImportDialog = false
                    else onErr(res.exceptionOrNull()?.localizedMessage ?: "Invalid configuration")
                }
            }
        )
    }
}

@Composable
private fun ServerNodeCard(
    server: VpnServer,
    isSelected: Boolean,
    measuredLatencyMs: Long?,
    onClick: () -> Unit,
    onDeleteCustom: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PurplePrimary else BorderLavender,
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("server_item_${server.id}"),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) PurpleTint else WhitePure
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) WhitePure else PurpleTint),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = server.flagEmoji.ifBlank { "\uD83C\uDF10" },
                        fontSize = 24.sp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = server.country,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        BrandPillBadge(
                            text = "FREE",
                            containerColor = StatusConnectedContainer,
                            contentColor = StatusConnected
                        )
                    }
                    Text(
                        text = "${server.city} • ${server.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = "Latency",
                                tint = when {
                                    measuredLatencyMs != null && measuredLatencyMs > 0 -> StatusConnected
                                    measuredLatencyMs == -1L -> StatusError
                                    else -> PurplePrimary
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = when {
                                    measuredLatencyMs != null && measuredLatencyMs > 0 -> "${measuredLatencyMs}ms"
                                    measuredLatencyMs == -1L -> "Unreachable"
                                    server.latencyMs > 0 -> "${server.latencyMs}ms"
                                    else -> "${server.endpointHost}:${server.endpointPort}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!server.isWireGuardReady) {
                            Text(
                                text = "• Needs WG Key",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusConnecting
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDeleteCustom != null) {
                    IconButton(onClick = onDeleteCustom) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete custom config",
                            tint = StatusError
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected server",
                        tint = PurplePrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportWireGuardDialog(
    onDismiss: () -> Unit,
    onImportConf: (name: String, confText: String, onError: (String) -> Unit) -> Unit,
    onSaveManual: (
        name: String,
        host: String,
        port: Long,
        pubKey: String,
        privKey: String,
        cidr: String,
        dns: String,
        allowedIps: String,
        onError: (String) -> Unit
    ) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var confText by remember { mutableStateOf("") }

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("51820") }
    var serverPubKey by remember { mutableStateOf("") }
    var clientPrivKey by remember { mutableStateOf("") }
    var clientCidr by remember { mutableStateOf("10.66.66.2/32") }
    var dns by remember { mutableStateOf("1.1.1.1, 1.0.0.1") }
    var allowedIps by remember { mutableStateOf("0.0.0.0/0, ::/0") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.VpnKey, contentDescription = null, tint = PurplePrimary)
                Text("Add WireGuard Node", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Paste .conf") })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Manual") })
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Node Name") },
                    placeholder = { Text("My VPS WireGuard") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (tabIndex == 0) {
                    OutlinedTextField(
                        value = confText,
                        onValueChange = { confText = it },
                        label = { Text("WireGuard Config ([Interface] & [Peer])") },
                        placeholder = { Text("[Interface]\nPrivateKey = ...\nAddress = 10.66.66.2/32\n\n[Peer]\nPublicKey = ...\nEndpoint = 1.2.3.4:51820\nAllowedIPs = 0.0.0.0/0") },
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Endpoint Host / IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Endpoint Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = serverPubKey,
                        onValueChange = { serverPubKey = it },
                        label = { Text("Server Public Key (Base64)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = clientPrivKey,
                        onValueChange = { clientPrivKey = it },
                        label = { Text("Client Private Key (optional, uses device key)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = clientCidr,
                        onValueChange = { clientCidr = it },
                        label = { Text("Tunnel IP CIDR") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dns,
                        onValueChange = { dns = it },
                        label = { Text("DNS") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = allowedIps,
                        onValueChange = { allowedIps = it },
                        label = { Text("Allowed IPs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg!!,
                        color = StatusError,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    errorMsg = null
                    if (tabIndex == 0) {
                        onImportConf(name, confText) { errorMsg = it }
                    } else {
                        onSaveManual(
                            name,
                            host,
                            port.toLongOrNull() ?: 51820L,
                            serverPubKey,
                            clientPrivKey,
                            clientCidr,
                            dns,
                            allowedIps
                        ) { errorMsg = it }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text("Save Node")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
