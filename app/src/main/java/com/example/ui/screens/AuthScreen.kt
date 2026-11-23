package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.attemptAutoSignIn
import com.example.data.onGoogleSignInClicked
import com.example.ui.components.BrandPillBadge
import com.example.ui.theme.BorderLavender
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.PurpleTint
import com.example.ui.theme.PurpleVibrant
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.SurfaceOffWhite
import com.example.ui.theme.WhitePure

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var isCheckingSilent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSilent = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingSilent = false
            },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PurpleTint, WhitePure, SurfaceOffWhite)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            contentDescription = "Fast X VPN Logo",
                            tint = WhitePure,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                BrandPillBadge(
                    text = stringResource(R.string.plan_free_badge),
                    containerColor = PurpleLight,
                    contentColor = PurplePrimary
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .border(1.dp, BorderLavender, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    color = WhitePure
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_vpn_hero),
                        contentDescription = "Fast X VPN WireGuard security globe",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = "Encrypted Speed.\nZero Cost. Pure WireGuard.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Connect across Bangladesh, USA, Singapore, UK, Canada & India with real Curve25519 WireGuard tunnels.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeatureRowItem(
                    icon = Icons.Filled.Lock,
                    title = "Real WireGuard® Protocol",
                    subtitle = "Hardware-grade Curve25519 keys generated locally on your device"
                )
                FeatureRowItem(
                    icon = Icons.Filled.Public,
                    title = "6 Global Free Regions",
                    subtitle = "Bangladesh, USA, Singapore, UK, Canada & India + custom nodes"
                )
                FeatureRowItem(
                    icon = Icons.Filled.Bolt,
                    title = "Live Speed & Auto-Reconnect",
                    subtitle = "Real-time tunnel statistics and seamless Wi-Fi / 5G failover"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = StatusErrorContainer
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusError,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading && !isCheckingSilent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurplePrimary,
                        contentColor = WhitePure
                    )
                ) {
                    if (isLoading || isCheckingSilent) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = WhitePure,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.sign_in_with_google),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Always Free • No subscriptions or credit card required",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeatureRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLavender, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = WhitePure
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PurpleLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PurplePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
