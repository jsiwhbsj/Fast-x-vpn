package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DefaultRegionTemplates
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.VpnServer
import com.example.ui.components.BrandPillBadge
import com.example.ui.components.MetricStatTile
import com.example.ui.components.formatBytes
import com.example.ui.theme.BorderLavender
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnectedContainer
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.SurfaceOffWhite
import com.example.ui.theme.WhitePure
import com.example.vpn.VpsHealthResponse

@Composable
fun AdminScreen(
    adminServersState: UiState<List<VpnServer>>,
    adminUsersState: UiState<List<UserProfile>>,
    vpsHealthMap: Map<String, Result<VpsHealthResponse>>,
    onSeedSixRegions: () -> Unit,
    onSaveServer: (VpnServer, isNew: Boolean, onDone: (Result<Unit>) -> Unit) -> Unit,
    onToggleServerActive: (serverId: String, active: Boolean) -> Unit,
    onDeleteServer: (serverId: String) -> Unit,
    onCheckVpsHealth: (VpnServer) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingServer by remember { mutableStateOf<Pair<VpnServer, Boolean>?>(null) }

    val servers = (adminServersState as? UiState.Success)?.data ?: emptyList()
    val users = (adminUsersState as? UiState.Success)?.data ?: emptyList()
    val activeCount = servers.count { it.active }
    val totalTrafficBytes = users.sumOf { it.totalBytesDown + it.totalBytesUp }

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
                            text = "Admin Control Panel",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Manage WireGuard VPS fleet & user telemetry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { editingServer = VpnServer() to true },
                        modifier = Modifier.testTag("admin_add_server_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add VPS Node", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Add Node")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatTile(
                        label = "Active Nodes",
                        value = "$activeCount/${servers.size}",
                        unit = "",
                        icon = Icons.Filled.Dns,
                        accentColor = PurplePrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatTile(
                        label = "Users",
                        value = "${users.size}",
                        unit = formatBytes(totalTrafficBytes),
                        icon = Icons.Filled.Group,
                        accentColor = StatusConnected,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedButton(
                    onClick = onSeedSixRegions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_seed_regions_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Seed Missing Default Regions (BD, US, SG, UK, CA, IN)")
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("VPS Nodes (${servers.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Users (${users.size})") }
                    )
                }
            }

            if (selectedTab == 0) {
                when (adminServersState) {
                    UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PurplePrimary)
                    }
                    is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(adminServersState.message, color = StatusError)
                    }
                    is UiState.Success -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(servers, key = { it.id }) { server ->
                                AdminServerCard(
                                    server = server,
                                    healthResult = vpsHealthMap[server.id],
                                    onToggleActive = { onToggleServerActive(server.id, it) },
                                    onEdit = { editingServer = server to false },
                                    onDelete = { onDeleteServer(server.id) },
                                    onTestHealth = { onCheckVpsHealth(server) }
                                )
                            }
                        }
                    }
                }
            } else {
                when (adminUsersState) {
                    UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PurplePrimary)
                    }
                    is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(adminUsersState.message, color = StatusError)
                    }
                    is UiState.Success -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(users, key = { it.userId.ifBlank { it.id } }) { user ->
                                AdminUserRow(user = user)
                            }
                        }
                    }
                }
            }
        }
    }

    editingServer?.let { (target, isNew) ->
        AdminServerEditorDialog(
            initial = target,
            isNew = isNew,
            onDismiss = { editingServer = null },
            onSave = { updated, onErr ->
                onSaveServer(updated, isNew) { res ->
                    if (res.isSuccess) editingServer = null
                    else onErr(res.exceptionOrNull()?.localizedMessage ?: "Failed to save server")
                }
            }
        )
    }
}

@Composable
private fun AdminServerCard(
    server: VpnServer,
    healthResult: Result<VpsHealthResponse>?,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestHealth: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLavender, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = WhitePure
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = server.flagEmoji.ifBlank { "\uD83C\uDF10" }, fontSize = 24.sp)
                    Column {
                        Text(
                            text = "${server.country} — ${server.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${server.endpointHost}:${server.endpointPort}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = server.active,
                    onCheckedChange = onToggleActive,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = WhitePure,
                        checkedTrackColor = PurplePrimary
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandPillBadge(
                    text = if (server.isWireGuardReady) "WG READY" else "NEEDS PUBKEY",
                    containerColor = if (server.isWireGuardReady) StatusConnectedContainer else StatusErrorContainer,
                    contentColor = if (server.isWireGuardReady) StatusConnected else StatusError
                )
                if (server.provisionApiUrl.isNotBlank()) {
                    BrandPillBadge(
                        text = "VPS API LINKED",
                        containerColor = PurpleLight,
                        contentColor = PurplePrimary
                    )
                }
            }

            healthResult?.let { res ->
                val healthMsg = res.fold(
                    onSuccess = { h -> "VPS OK • ${h.interfaceName} • ${h.activePeers} peers • Load ${h.loadPercent}%" },
                    onFailure = { e -> "VPS Health Error: ${e.localizedMessage}" }
                )
                Text(
                    text = healthMsg,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (res.isSuccess) StatusConnected else StatusError
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (server.provisionApiUrl.isNotBlank()) {
                    TextButton(onClick = onTestHealth) {
                        Icon(Icons.Filled.HealthAndSafety, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Test VPS")
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit server", tint = PurplePrimary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete server", tint = StatusError)
                }
            }
        }
    }
}

@Composable
private fun AdminUserRow(user: UserProfile) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLavender, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = WhitePure
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (user.lastServerName.isNotBlank()) {
                    Text(
                        text = "Last: ${user.lastServerName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PurplePrimary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                BrandPillBadge(
                    text = "${user.totalSessions} sessions",
                    containerColor = PurpleLight,
                    contentColor = PurplePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatBytes(user.totalBytesDown + user.totalBytesUp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AdminServerEditorDialog(
    initial: VpnServer,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (VpnServer, onError: (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var country by remember { mutableStateOf(initial.country) }
    var countryCode by remember { mutableStateOf(initial.countryCode) }
    var city by remember { mutableStateOf(initial.city) }
    var flagEmoji by remember { mutableStateOf(initial.flagEmoji) }
    var endpointHost by remember { mutableStateOf(initial.endpointHost) }
    var endpointPort by remember { mutableStateOf(initial.endpointPort.toString()) }
    var serverPublicKey by remember { mutableStateOf(initial.serverPublicKey) }
    var provisionApiUrl by remember { mutableStateOf(initial.provisionApiUrl) }
    var clientAddressCidr by remember { mutableStateOf(initial.clientAddressCidr) }
    var dns by remember { mutableStateOf(initial.dns) }
    var allowedIps by remember { mutableStateOf(initial.allowedIps) }
    var mtu by remember { mutableStateOf(initial.mtu.toString()) }
    var active by remember { mutableStateOf(initial.active) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CloudDone, contentDescription = null, tint = PurplePrimary)
                Text(if (isNew) "Add WireGuard VPS Node" else "Edit VPS Node", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isNew) {
                    Text("Quick Region Preset:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DefaultRegionTemplates.sixCoreRegions.forEach { preset ->
                            FilterChip(
                                selected = countryCode == preset.countryCode,
                                onClick = {
                                    name = preset.name
                                    country = preset.country
                                    countryCode = preset.countryCode
                                    city = preset.city
                                    flagEmoji = preset.flagEmoji
                                    endpointHost = preset.endpointHost
                                    endpointPort = preset.endpointPort.toString()
                                    clientAddressCidr = preset.clientAddressCidr
                                },
                                label = { Text("${preset.flagEmoji} ${preset.country}") }
                            )
                        }
                    }
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Node Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text("Country") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = countryCode, onValueChange = { countryCode = it }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(0.5f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = flagEmoji, onValueChange = { flagEmoji = it }, label = { Text("Flag") }, singleLine = true, modifier = Modifier.weight(0.5f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = endpointHost, onValueChange = { endpointHost = it }, label = { Text("VPS IP / Host") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endpointPort, onValueChange = { endpointPort = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(0.5f))
                }
                OutlinedTextField(
                    value = serverPublicKey,
                    onValueChange = { serverPublicKey = it },
                    label = { Text("WireGuard Server Public Key (Base64)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = provisionApiUrl,
                    onValueChange = { provisionApiUrl = it },
                    label = { Text("VPS Provisioning API URL (optional)") },
                    placeholder = { Text("https://bd1.fastxvpn.net:8443") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = clientAddressCidr, onValueChange = { clientAddressCidr = it }, label = { Text("Default Client CIDR") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dns, onValueChange = { dns = it }, label = { Text("DNS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = allowedIps, onValueChange = { allowedIps = it }, label = { Text("Allowed IPs") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mtu, onValueChange = { mtu = it.filter(Char::isDigit) }, label = { Text("MTU") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Node Active (Visible to Users)")
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(text = errorMsg!!, color = StatusError, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    errorMsg = null
                    val updated = initial.copy(
                        name = name,
                        country = country,
                        countryCode = countryCode,
                        city = city,
                        flagEmoji = flagEmoji.ifBlank { "\uD83C\uDF10" },
                        endpointHost = endpointHost,
                        endpointPort = endpointPort.toLongOrNull() ?: 51820L,
                        serverPublicKey = serverPublicKey,
                        provisionApiUrl = provisionApiUrl,
                        clientAddressCidr = clientAddressCidr,
                        dns = dns,
                        allowedIps = allowedIps,
                        mtu = (mtu.toLongOrNull() ?: 1280L).coerceIn(1200L, 1500L),
                        active = active
                    )
                    onSave(updated) { errorMsg = it }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text(if (isNew) "Create Node" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
