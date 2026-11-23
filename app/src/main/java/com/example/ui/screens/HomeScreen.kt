package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.SpeedTelemetry
import com.example.data.VpnConnectionState
import com.example.data.VpnServer
import com.example.ui.components.BrandPillBadge
import com.example.ui.components.LiveThroughputSparkline
import com.example.ui.components.MetricStatTile
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.components.formatSpeedBps
import com.example.ui.theme.BorderLavender
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.PurpleTint
import com.example.ui.theme.PurpleVibrant
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnectedContainer
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.SurfaceOffWhite
import com.example.ui.theme.WhitePure

@Composable
fun HomeScreen(
    connectionState: VpnConnectionState,
    telemetry: SpeedTelemetry,
    selectedServer: VpnServer?,
    measuredLatencies: Map<String, Long>,
    autoReconnect: Boolean,
    onToggleConnection: () -> Unit,
    onOpenServers: () -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = connectionState is VpnConnectionState.Connected
    val isConnecting = connectionState is VpnConnectionState.Connecting ||
        connectionState is VpnConnectionState.Reconnecting

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isConnected || isConnecting) 1.15f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val ringColor by animateColorAsState(
        targetValue = when (connectionState) {
            is VpnConnectionState.Connected -> StatusConnected
            is VpnConnectionState.Connecting,
            is VpnConnectionState.Reconnecting -> StatusConnecting
            is VpnConnectionState.Error -> StatusError
            VpnConnectionState.Disconnected -> PurplePrimary
        },
        label = "ring_color"
    )

    val (downVal, downUnit) = formatSpeedBps(telemetry.downloadBytesPerSec)
    val (upVal, upUnit) = formatSpeedBps(telemetry.uploadBytesPerSec)

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Brand Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PurplePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Fast X VPN",
                            tint = WhitePure,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "WireGuard® Tunnel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                BrandPillBadge(
                    text = "FREE",
                    containerColor = PurpleLight,
                    contentColor = PurplePrimary,
                    leadingDotColor = StatusConnected
                )
            }

            // Status Pill + Connection Hero
            val statusLabel = when (connectionState) {
                VpnConnectionState.Disconnected -> stringResource(R.string.vpn_disconnected)
                is VpnConnectionState.Connecting -> connectionState.stage
                is VpnConnectionState.Connected -> stringResource(R.string.vpn_connected)
                is VpnConnectionState.Reconnecting -> "Auto-Reconnecting (#${connectionState.attempt})…"
                is VpnConnectionState.Error -> connectionState.message
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderLavender, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = WhitePure
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    BrandPillBadge(
                        text = statusLabel,
                        containerColor = when (connectionState) {
                            is VpnConnectionState.Connected -> StatusConnectedContainer
                            is VpnConnectionState.Error -> StatusErrorContainer
                            else -> PurpleTint
                        },
                        contentColor = when (connectionState) {
                            is VpnConnectionState.Connected -> StatusConnected
                            is VpnConnectionState.Error -> StatusError
                            else -> PurplePrimary
                        },
                        leadingDotColor = ringColor
                    )

                    // Interactive Power Pulse Shield Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(188.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(176.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(ringColor.copy(alpha = 0.12f))
                        )
                        Box(
                            modifier = Modifier
                                .size(148.dp)
                                .clip(CircleShape)
                                .background(ringColor.copy(alpha = 0.20f))
                        )
                        Surface(
                            modifier = Modifier
                                .size(120.dp)
                                .shadow(12.dp, CircleShape)
                                .clip(CircleShape)
                                .clickable(onClick = onToggleConnection)
                                .testTag("vpn_connect_button"),
                            shape = CircleShape,
                            color = if (isConnected) StatusConnected else PurplePrimary
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isConnected) {
                                            Brush.linearGradient(listOf(StatusConnected, ColorEmeraldDeep))
                                        } else {
                                            Brush.linearGradient(listOf(PurplePrimary, PurpleVibrant))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PowerSettingsNew,
                                        contentDescription = if (isConnected) "Disconnect VPN" else "Connect VPN",
                                        tint = WhitePure,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Text(
                                        text = when {
                                            isConnected -> "PROTECTED"
                                            isConnecting -> "CONNECTING"
                                            else -> "CONNECT"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = WhitePure
                                    )
                                }
                            }
                        }
                    }

                    // Duration + Handshake State Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Session timer",
                                tint = PurplePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formatDuration(telemetry.durationSeconds),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = "WireGuard Handshake",
                                tint = if (telemetry.hasActiveHandshake) StatusConnected else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    !isConnected -> "Tunnel Idle"
                                    telemetry.hasActiveHandshake -> "Handshake Verified"
                                    else -> "Awaiting Peer Handshake"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Selected Server Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderLavender, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onOpenServers)
                    .testTag("selected_server_card"),
                shape = RoundedCornerShape(22.dp),
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
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PurpleTint),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedServer != null) {
                                Text(
                                    text = selectedServer.flagEmoji.ifBlank { "\uD83C\uDF10" },
                                    fontSize = 24.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = "Select server",
                                    tint = PurplePrimary
                                )
                            }
                        }
                        Column {
                            Text(
                                text = selectedServer?.country ?: "No Server Selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = selectedServer?.let {
                                    val ping = measuredLatencies[it.id] ?: it.latencyMs
                                    val pingText = if (ping > 0) "${ping}ms" else "Tap to test ping"
                                    "${it.city} • ${it.name} • $pingText"
                                } ?: "Tap to choose from 6 free locations or import WireGuard config",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Change server",
                        tint = PurplePrimary
                    )
                }
            }

            // Live Speed Monitor Section
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderLavender, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = WhitePure
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Speed Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Rx ${formatBytes(telemetry.totalRxBytes)} • Tx ${formatBytes(telemetry.totalTxBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PurplePrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricStatTile(
                            label = "Download",
                            value = downVal,
                            unit = downUnit,
                            icon = Icons.Filled.ArrowDownward,
                            accentColor = PurplePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatTile(
                            label = "Upload",
                            value = upVal,
                            unit = upUnit,
                            icon = Icons.Filled.ArrowUpward,
                            accentColor = StatusConnected,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LiveThroughputSparkline(
                        downloadSamples = telemetry.downloadHistoryBps,
                        uploadSamples = telemetry.uploadHistoryBps
                    )
                }
            }

            // Auto-Reconnect Quick Control
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(PurpleTint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Autorenew,
                                contentDescription = "Auto-Reconnect",
                                tint = PurplePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Auto-Reconnect",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Restore WireGuard automatically on Wi-Fi/5G switch",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = autoReconnect,
                        onCheckedChange = onToggleAutoReconnect,
                        modifier = Modifier.testTag("home_auto_reconnect_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WhitePure,
                            checkedTrackColor = PurplePrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

private val ColorEmeraldDeep = androidx.compose.ui.graphics.Color(0xFF059669)
