package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.VpnConnectionState
import com.example.data.VpnRepository
import com.example.data.signOut
import com.example.ui.VpnViewModel
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.theme.FastXVpnTheme
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.WhitePure
import com.example.vpn.VpsProvisioningClient
import com.example.vpn.WireGuardKeyStore
import com.example.vpn.WireGuardVpnManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

private enum class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("home", R.string.nav_home, Icons.Filled.Shield, Icons.Outlined.Shield, "nav_home"),
    SERVERS("servers", R.string.nav_servers, Icons.Filled.Public, Icons.Outlined.Public, "nav_servers"),
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    ADMIN("admin", R.string.nav_admin, Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings, "nav_admin")
}

class MainActivity : ComponentActivity() {
    private val keyStore by lazy { WireGuardKeyStore(applicationContext) }
    private val vpsClient by lazy { VpsProvisioningClient() }
    private val vpnManager by lazy { WireGuardVpnManager(applicationContext, keyStore, vpsClient) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FastXVpnTheme {
                FastXVpnRoot(
                    vpnManager = vpnManager,
                    keyStore = keyStore,
                    vpsClient = vpsClient
                )
            }
        }
    }
}

@Composable
private fun FastXVpnRoot(
    vpnManager: WireGuardVpnManager,
    keyStore: WireGuardKeyStore,
    vpsClient: VpsProvisioningClient
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            AuthenticatedVpnShell(
                currentUserId = user.uid,
                vpnManager = vpnManager,
                keyStore = keyStore,
                vpsClient = vpsClient
            )
        }
    }
}

@Composable
private fun AuthenticatedVpnShell(
    currentUserId: String,
    vpnManager: WireGuardVpnManager,
    keyStore: WireGuardKeyStore,
    vpsClient: VpsProvisioningClient
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: VpnViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                VpnViewModel(
                    repository = VpnRepository(app),
                    vpnManager = vpnManager,
                    keyStore = keyStore,
                    vpsClient = vpsClient,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val devicePublicKey by viewModel.devicePublicKey.collectAsStateWithLifecycle()
    val autoReconnect by viewModel.autoReconnect.collectAsStateWithLifecycle()
    val measuredLatencies by viewModel.measuredLatencies.collectAsStateWithLifecycle()
    val isPinging by viewModel.isPinging.collectAsStateWithLifecycle()
    val vpsHealthMap by viewModel.vpsHealthMap.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val activeServersState by viewModel.activeServersState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val customConfigsState by viewModel.customConfigsState.collectAsStateWithLifecycle()
    val adminServersState by viewModel.adminServersState.collectAsStateWithLifecycle()
    val adminUsersState by viewModel.adminUsersState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connectSelectedServer()
        } else {
            viewModel.notifyMessage(context.getString(R.string.error_vpn_permission_denied))
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val vpnIntent = viewModel.prepareVpnPermission()
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            viewModel.connectSelectedServer()
        }
    }

    fun startVpnConnectionFlow() {
        val state = connectionState
        if (state is VpnConnectionState.Connected ||
            state is VpnConnectionState.Connecting ||
            state is VpnConnectionState.Reconnecting
        ) {
            viewModel.disconnectVpn()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        val vpnIntent = viewModel.prepareVpnPermission()
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            viewModel.connectSelectedServer()
        }
    }

    val destinations = remember(isAdmin) {
        if (isAdmin) {
            listOf(NavDestination.HOME, NavDestination.SERVERS, NavDestination.DASHBOARD, NavDestination.ADMIN)
        } else {
            listOf(NavDestination.HOME, NavDestination.SERVERS, NavDestination.DASHBOARD)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    fun navigateTo(dest: NavDestination) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = WhitePure) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PurplePrimary,
                                    selectedTextColor = PurplePrimary,
                                    indicatorColor = PurpleLight
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = WhitePure) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = PurplePrimary,
                                    selectedTextColor = PurplePrimary,
                                    indicatorColor = PurpleLight
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = NavDestination.HOME.route
                    ) {
                        composable(NavDestination.HOME.route) {
                            HomeScreen(
                                connectionState = connectionState,
                                telemetry = telemetry,
                                selectedServer = selectedServer,
                                measuredLatencies = measuredLatencies,
                                autoReconnect = autoReconnect,
                                onToggleConnection = { startVpnConnectionFlow() },
                                onOpenServers = { navigateTo(NavDestination.SERVERS) },
                                onToggleAutoReconnect = viewModel::toggleAutoReconnect
                            )
                        }
                        composable(NavDestination.SERVERS.route) {
                            ServersScreen(
                                serversState = activeServersState,
                                customConfigsState = customConfigsState,
                                selectedServerId = selectedServer?.id,
                                measuredLatencies = measuredLatencies,
                                isPinging = isPinging,
                                isAdmin = isAdmin,
                                onSelectServer = { server ->
                                    viewModel.selectServer(server)
                                    navigateTo(NavDestination.HOME)
                                },
                                onPingServers = viewModel::pingServers,
                                onImportWireGuardConf = viewModel::importCustomConfig,
                                onSaveManualConfig = viewModel::saveManualCustomConfig,
                                onDeleteCustomConfig = viewModel::deleteCustomConfig,
                                onSeedSixRegions = viewModel::seedDefaultSixRegions
                            )
                        }
                        composable(NavDestination.DASHBOARD.route) {
                            DashboardScreen(
                                profileState = profileState,
                                sessionsState = sessionsState,
                                devicePublicKey = devicePublicKey,
                                autoReconnect = autoReconnect,
                                isAdmin = isAdmin,
                                onToggleAutoReconnect = viewModel::toggleAutoReconnect,
                                onCopiedPublicKey = { viewModel.notifyMessage("WireGuard public key copied to clipboard.") },
                                onSignOut = {
                                    viewModel.disconnectVpn()
                                    signOut(credentialManager, scope)
                                }
                            )
                        }
                        if (isAdmin) {
                            composable(NavDestination.ADMIN.route) {
                                AdminScreen(
                                    adminServersState = adminServersState,
                                    adminUsersState = adminUsersState,
                                    vpsHealthMap = vpsHealthMap,
                                    onSeedSixRegions = viewModel::seedDefaultSixRegions,
                                    onSaveServer = viewModel::saveAdminServer,
                                    onToggleServerActive = viewModel::toggleServerActive,
                                    onDeleteServer = viewModel::deleteAdminServer,
                                    onCheckVpsHealth = viewModel::checkVpsHealth
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
