package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AiInsightsScreen
import com.example.ui.AlertsScreen
import com.example.ui.AppScreen
import com.example.ui.BatchDetailScreen
import com.example.ui.BatchesScreen
import com.example.ui.DashboardScreen
import com.example.ui.DeviceDetailScreen
import com.example.ui.DevicesScreen
import com.example.ui.MainViewModel
import com.example.ui.PublicVerifyScreen
import com.example.ui.QrScannerScreen
import com.example.ui.SettingsScreen
import com.example.ui.TraceabilityScreen
import com.example.ui.components.ConnectionStateBadge
import com.example.ui.theme.FarmTraceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FarmTraceTheme {
                FarmTraceApp(viewModel = viewModel)
            }
        }
    }
}

private data class NavItem(
    val screen: AppScreen,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmTraceApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val unreadAlertsCount by viewModel.unacknowledgedAlertCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Back handling for sub-screens
    BackHandler(enabled = uiState.currentScreen != AppScreen.DASHBOARD) {
        when (uiState.currentScreen) {
            AppScreen.BATCH_DETAIL -> viewModel.navigateTo(AppScreen.BATCHES)
            AppScreen.DEVICE_DETAIL -> viewModel.navigateTo(AppScreen.DEVICES)
            AppScreen.PUBLIC_VERIFY -> viewModel.navigateTo(AppScreen.BATCH_DETAIL)
            AppScreen.QR_SCANNER -> viewModel.navigateTo(AppScreen.DASHBOARD)
            else -> viewModel.navigateTo(AppScreen.DASHBOARD)
        }
    }

    val navItems = listOf(
        NavItem(AppScreen.DASHBOARD, "Dashboard", Icons.Default.Dashboard, "nav_dashboard"),
        NavItem(AppScreen.BATCHES, "Batches", Icons.Default.Eco, "nav_batches"),
        NavItem(AppScreen.DEVICES, "Nodes", Icons.Default.Sensors, "nav_devices"),
        NavItem(AppScreen.TRACEABILITY, "Ledger", Icons.Default.Hub, "nav_traceability"),
        NavItem(AppScreen.ALERTS, "Alerts", Icons.Default.Notifications, "nav_alerts"),
        NavItem(AppScreen.SETTINGS, "Settings", Icons.Default.Settings, "nav_settings")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.currentScreen != AppScreen.QR_SCANNER) {
                TopAppBar(
                    title = {
                        Text(
                            text = "FarmTrace",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.navigateTo(AppScreen.QR_SCANNER) },
                            modifier = Modifier.testTag("top_bar_scan_qr_btn")
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Node QR",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        ConnectionStateBadge(
                            state = uiState.connectionState,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (uiState.currentScreen != AppScreen.QR_SCANNER) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    navItems.forEach { item ->
                        val isSelected = when (item.screen) {
                            AppScreen.BATCHES -> uiState.currentScreen == AppScreen.BATCHES || uiState.currentScreen == AppScreen.BATCH_DETAIL
                            AppScreen.DEVICES -> uiState.currentScreen == AppScreen.DEVICES || uiState.currentScreen == AppScreen.DEVICE_DETAIL
                            else -> uiState.currentScreen == item.screen
                        }

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.navigateTo(item.screen) },
                            icon = {
                                if (item.screen == AppScreen.ALERTS && unreadAlertsCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$unreadAlertsCount") } }) {
                                        Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp))
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp))
                                }
                            },
                            label = { Text(item.label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentScreen) {
                AppScreen.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToBatches = { viewModel.navigateTo(AppScreen.BATCHES) },
                    onNavigateToDevices = { viewModel.navigateTo(AppScreen.DEVICES) },
                    onNavigateToAlerts = { viewModel.navigateTo(AppScreen.ALERTS) },
                    onNavigateToTraceability = { batchId ->
                        viewModel.selectBatch(batchId, navigateToDetail = false)
                        viewModel.navigateTo(AppScreen.TRACEABILITY)
                    },
                    onNavigateToSettings = { viewModel.navigateTo(AppScreen.SETTINGS) }
                )

                AppScreen.BATCHES -> BatchesScreen(
                    viewModel = viewModel,
                    onSelectBatch = { batchId -> viewModel.selectBatch(batchId) },
                    onOpenPublicVerify = { batchId ->
                        viewModel.selectBatch(batchId, navigateToDetail = false)
                        viewModel.navigateTo(AppScreen.PUBLIC_VERIFY)
                    }
                )

                AppScreen.BATCH_DETAIL -> BatchDetailScreen(
                    batchId = uiState.selectedBatchId ?: "",
                    viewModel = viewModel,
                    onBack = { viewModel.navigateTo(AppScreen.BATCHES) },
                    onNavigateToTraceability = { batchId ->
                        viewModel.selectBatch(batchId, navigateToDetail = false)
                        viewModel.navigateTo(AppScreen.TRACEABILITY)
                    },
                    onNavigateToAiInsights = { batchId ->
                        viewModel.selectBatch(batchId, navigateToDetail = false)
                        viewModel.navigateTo(AppScreen.AI_INSIGHTS)
                    },
                    onNavigateToPublicVerify = { batchId ->
                        viewModel.selectBatch(batchId, navigateToDetail = false)
                        viewModel.navigateTo(AppScreen.PUBLIC_VERIFY)
                    }
                )

                AppScreen.DEVICES -> DevicesScreen(
                    viewModel = viewModel,
                    onSelectDevice = { deviceId -> viewModel.selectDevice(deviceId) }
                )

                AppScreen.DEVICE_DETAIL -> DeviceDetailScreen(
                    deviceId = uiState.selectedDeviceId ?: "",
                    viewModel = viewModel,
                    onBack = { viewModel.navigateTo(AppScreen.DEVICES) }
                )

                AppScreen.TRACEABILITY -> TraceabilityScreen(
                    initialBatchId = uiState.selectedBatchId,
                    viewModel = viewModel
                )

                AppScreen.ALERTS -> AlertsScreen(
                    viewModel = viewModel
                )

                AppScreen.AI_INSIGHTS -> AiInsightsScreen(
                    initialBatchId = uiState.selectedBatchId,
                    viewModel = viewModel
                )

                AppScreen.PUBLIC_VERIFY -> PublicVerifyScreen(
                    batchId = uiState.selectedBatchId ?: "",
                    viewModel = viewModel,
                    onBack = { viewModel.navigateTo(AppScreen.BATCH_DETAIL) }
                )

                AppScreen.QR_SCANNER -> QrScannerScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) }
                )

                AppScreen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
