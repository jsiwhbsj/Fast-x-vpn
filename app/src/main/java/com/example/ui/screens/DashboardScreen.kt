package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.VpnSession
import com.example.ui.components.BrandPillBadge
import com.example.ui.components.MetricStatTile
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.theme.BorderLavender
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.PurpleTint
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnectedContainer
import com.example.ui.theme.SurfaceOffWhite
import com.example.ui.theme.WhitePure
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DashboardScreen(
    profileState: UiState<UserProfile?>,
    sessionsState: UiState<List<VpnSession>>,
    devicePublicKey: String,
    autoReconnect: Boolean,
    isAdmin: Boolean,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onCopiedPublicKey: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val profile = (profileState as? UiState.Success)?.data
    val sessions = (sessionsState as? UiState.Success)?.data ?: emptyList()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceOffWhite),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "User Dashboard",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Profile Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderLavender, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = WhitePure
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(PurpleLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!profile?.photoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = profile?.photoUrl,
                                            contentDescription = "Google Avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = "User Avatar",
                                            tint = PurplePrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = profile?.displayName?.ifBlank { "Fast X User" } ?: "Fast X User",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isAdmin) {
                                            Icon(
                                                imageVector = Icons.Filled.Verified,
                                                contentDescription = "Admin",
                                                tint = PurplePrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = profile?.email ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("sign_out_button"),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign out",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Sign Out")
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrandPillBadge(
                                text = "PLAN: COMPLETELY FREE",
                                containerColor = StatusConnectedContainer,
                                contentColor = StatusConnected
                            )
                            if (isAdmin) {
                                BrandPillBadge(
                                    text = "ADMIN ACCESS",
                                    containerColor = PurpleLight,
                                    contentColor = PurplePrimary
                                )
                            }
                        }
                    }
                }
            }

            // Aggregate Traffic Stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricStatTile(
                        label = "Total Download",
                        value = formatBytes(profile?.totalBytesDown ?: 0L),
                        unit = "",
                        icon = Icons.Filled.ArrowDownward,
                        accentColor = PurplePrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatTile(
                        label = "Total Upload",
                        value = formatBytes(profile?.totalBytesUp ?: 0L),
                        unit = "",
                        icon = Icons.Filled.ArrowUpward,
                        accentColor = StatusConnected,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricStatTile(
                        label = "Sessions",
                        value = "${profile?.totalSessions ?: 0L}",
                        unit = "runs",
                        icon = Icons.Filled.History,
                        accentColor = PurplePrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatTile(
                        label = "Protected Time",
                        value = formatDuration(profile?.totalConnectedSeconds ?: 0L),
                        unit = "",
                        icon = Icons.Filled.Timer,
                        accentColor = PurplePrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Device WireGuard Curve25519 Public Key Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderLavender, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    color = WhitePure
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PurpleTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = "WireGuard Public Key",
                                    tint = PurplePrimary
                                )
                            }
                            Column {
                                Text(
                                    text = "Device WireGuard Public Key",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = devicePublicKey.ifBlank { "Generating Curve25519 key…" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (devicePublicKey.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(devicePublicKey))
                                    onCopiedPublicKey()
                                }
                            },
                            modifier = Modifier.testTag("copy_wg_pubkey_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy public key",
                                tint = PurplePrimary
                            )
                        }
                    }
                }
            }

            // Auto-Reconnect Preference
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderLavender, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    color = WhitePure
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Reconnect Protection",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Automatically re-establish WireGuard when switching networks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoReconnect,
                            onCheckedChange = onToggleAutoReconnect,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WhitePure,
                                checkedTrackColor = PurplePrimary
                            )
                        )
                    }
                }
            }

            // Recent Sessions Header
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Recent WireGuard Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            when (sessionsState) {
                UiState.Loading -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PurplePrimary)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(sessionsState.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is UiState.Success -> {
                    if (sessions.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BorderLavender, RoundedCornerShape(18.dp)),
                                shape = RoundedCornerShape(18.dp),
                                color = WhitePure
                            ) {
                                Text(
                                    text = "No completed WireGuard sessions yet. Connect to a server and disconnect to record session telemetry.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(sessions.take(15), key = { it.id }) { session ->
                            SessionHistoryCard(session = session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: VpnSession) {
    val dateStr = session.createdAt?.toDate()?.let {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(it)
    } ?: "Just now"

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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${session.country} • ${session.serverName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$dateStr • ${formatDuration(session.durationSeconds)} • ${session.protocol}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "↓ ${formatBytes(session.bytesDown)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PurplePrimary
                )
                Text(
                    text = "↑ ${formatBytes(session.bytesUp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusConnected
                )
            }
        }
    }
}
