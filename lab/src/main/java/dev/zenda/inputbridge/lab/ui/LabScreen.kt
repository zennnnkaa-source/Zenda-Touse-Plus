package dev.zenda.inputbridge.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.zenda.inputbridge.lab.data.local.ProbeRunEntity
import java.text.DateFormat
import java.util.Date

private enum class LabTab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    SHIZUKU("shizuku", "Shizuku", Icons.Filled.Security),
    DIRECT_ADB("direct_adb", "Direct", Icons.Filled.Wifi),
    DIAGNOSTICS("diagnostics", "Diagnostics", Icons.Filled.BugReport),
    SETTINGS("settings", "Settings", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(
    shizukuStatus: String,
    helperStatus: String,
    adbStatus: String,
    recentRuns: List<ProbeRunEntity>,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onBindHelper: () -> Unit,
    onUnbindHelper: () -> Unit,
    onOpenTarget: () -> Unit,
    onGetHelperResult: () -> Unit,
    onShizukuTapProbe: () -> Unit,
    onShizukuSwipeProbe: () -> Unit,
    onPair: (String, Int, String) -> Unit,
    onConnect: (String, Int) -> Unit,
    onStartAdbHelper: () -> Unit,
    onStopAdbHelper: () -> Unit,
    onDisconnect: () -> Unit,
    onAdbTapProbe: () -> Unit,
    onAdbSwipeProbe: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    var host by rememberSaveable { mutableStateOf("127.0.0.1") }
    var pairingPort by rememberSaveable { mutableStateOf("") }
    var connectionPort by rememberSaveable { mutableStateOf("") }
    // A pairing code is intentionally not saveable: don't persist it through recreation.
    var pairingCode by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("InputBridge", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Zenda Touse Plus · Feasibility Lab", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (darkTheme) "Switch to light theme" else "Switch to dark theme"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                LabTab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LabTab.HOME.route,
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            composable(LabTab.HOME.route) {
                HomeScreen(
                    shizukuStatus = shizukuStatus,
                    adbStatus = adbStatus,
                    onOpenShizuku = { navController.navigate(LabTab.SHIZUKU.route) },
                    onOpenDirect = { navController.navigate(LabTab.DIRECT_ADB.route) },
                    onOpenTarget = onOpenTarget
                )
            }
            composable(LabTab.SHIZUKU.route) {
                ShizukuScreen(
                    shizukuStatus = shizukuStatus,
                    helperStatus = helperStatus,
                    onRefresh = onRefreshShizuku,
                    onRequestPermission = onRequestShizukuPermission,
                    onBind = onBindHelper,
                    onUnbind = onUnbindHelper,
                    onTap = onShizukuTapProbe,
                    onSwipe = onShizukuSwipeProbe,
                    onReadResult = onGetHelperResult,
                    onOpenTarget = onOpenTarget
                )
            }
            composable(LabTab.DIRECT_ADB.route) {
                DirectAdbScreen(
                    host = host,
                    onHostChange = { host = it.take(253) },
                    pairingPort = pairingPort,
                    onPairingPortChange = { pairingPort = it.filter(Char::isDigit).take(5) },
                    connectionPort = connectionPort,
                    onConnectionPortChange = { connectionPort = it.filter(Char::isDigit).take(5) },
                    pairingCode = pairingCode,
                    onPairingCodeChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                    adbStatus = adbStatus,
                    onPair = {
                        val code = pairingCode
                        pairingCode = ""
                        onPair(host, pairingPort.toIntOrNull() ?: -1, code)
                    },
                    onConnect = { onConnect(host, connectionPort.toIntOrNull() ?: -1) },
                    onStartHelper = onStartAdbHelper,
                    onStopHelper = onStopAdbHelper,
                    onDisconnect = onDisconnect,
                    onTap = onAdbTapProbe,
                    onSwipe = onAdbSwipeProbe,
                    onOpenTarget = onOpenTarget
                )
            }
            composable(LabTab.DIAGNOSTICS.route) {
                DiagnosticsScreen(
                    helperStatus = helperStatus,
                    adbStatus = adbStatus,
                    recentRuns = recentRuns,
                    onOpenTarget = onOpenTarget
                )
            }
            composable(LabTab.SETTINGS.route) {
                SettingsScreen(darkTheme = darkTheme, onToggleTheme = onToggleTheme)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    shizukuStatus: String,
    adbStatus: String,
    onOpenShizuku: () -> Unit,
    onOpenDirect: () -> Unit,
    onOpenTarget: () -> Unit
) {
    ContentColumn {
        HeroBanner()
        SectionHeading("Activation paths", "Two independent ways to start the test helper")
        StatusCard(
            title = "Shizuku",
            subtitle = "Primary path · Binder and permission probes",
            status = shizukuStatus,
            icon = Icons.Filled.Security,
            onClick = onOpenShizuku
        )
        StatusCard(
            title = "Direct Wireless debugging",
            subtitle = "Experimental · Pair, connect and bootstrap helper",
            status = adbStatus,
            icon = Icons.Filled.Wifi,
            onClick = onOpenDirect
        )
        SectionHeading("Test safely", "Use the dedicated target app, not a game")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text("InputBridge Test Target", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Checks real events delivered to a separate Activity.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick = onOpenTarget, modifier = Modifier.fillMaxWidth()) { Text("Open test target") }
            }
        }
        InfoNotice("No compatibility claims", "A successful bind, ADB shell response, or helper UID is not proof that touch input reached the target.")
    }
}

@Composable
private fun HeroBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF143F46), Color(0xFF202D57), Color(0xFF30264F))
                )
            )
            .padding(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = Color.White.copy(alpha = 0.12f), shape = CircleShape) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF6DE7D0)))
                    Text("PHASE 1 · FEASIBILITY LAB", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Text("Make every input\nmeasurable.", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Verify each activation step with evidence before building the full mapping engine.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE0E9FF)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniMetric("2", "activation paths")
                MiniMetric("1", "dedicated test target")
            }
        }
    }
}

@Composable
private fun MiniMetric(value: String, label: String) {
    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, color = Color(0xFFD5E3FF), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ShizukuScreen(
    shizukuStatus: String,
    helperStatus: String,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onBind: () -> Unit,
    onUnbind: () -> Unit,
    onTap: () -> Unit,
    onSwipe: () -> Unit,
    onReadResult: () -> Unit,
    onOpenTarget: () -> Unit
) {
    ContentColumn {
        ScreenHeading("Shizuku", "Primary activation path", "Check the live Binder and app permission before binding the helper.")
        StatusDetailCard("Shizuku status", shizukuStatus, Icons.Filled.Security)
        ActionGrid {
            ActionButton("Check status", onClick = onRefresh)
            ActionButton("Request permission", onClick = onRequestPermission)
            ActionButton("Bind helper", onClick = onBind)
            ActionButton("Unbind helper", onClick = onUnbind, secondary = true)
        }
        SectionHeading("Helper report", "The reported UID is one capability check, not input delivery")
        StatusDetailCard("Current helper", helperStatus, Icons.Filled.Info)
        ActionButton("Read latest helper result", onClick = onReadResult)
        SectionHeading("Input probes", "Open the target first or allow the Lab to open it")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onTap, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.TouchApp, null); Spacer(Modifier.width(8.dp)); Text("Tap") }
            OutlinedButton(onClick = onSwipe, modifier = Modifier.weight(1f)) { Text("Swipe 500 ms") }
        }
        OutlinedButton(onClick = onOpenTarget, modifier = Modifier.fillMaxWidth()) { Text("Open test target") }
        InfoNotice("Honest result", "The app reports helper responses separately. Inspect the Test Target event log before marking this path as passed.")
    }
}

@Composable
private fun DirectAdbScreen(
    host: String,
    onHostChange: (String) -> Unit,
    pairingPort: String,
    onPairingPortChange: (String) -> Unit,
    connectionPort: String,
    onConnectionPortChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    adbStatus: String,
    onPair: () -> Unit,
    onConnect: () -> Unit,
    onStartHelper: () -> Unit,
    onStopHelper: () -> Unit,
    onDisconnect: () -> Unit,
    onTap: () -> Unit,
    onSwipe: () -> Unit,
    onOpenTarget: () -> Unit
) {
    ContentColumn {
        ScreenHeading("Direct Wireless debugging", "Experimental pathway", "Pairing, shell access and input delivery are different checkpoints.")
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Experimental · not verified", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                    Text("Keep the Android Pair device screen accessible. Pairing and connection ports are separate and can change.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        StatusDetailCard("Latest ADB report", adbStatus, Icons.Filled.Wifi)
        SectionHeading("1 · Pair device", "Enter the temporary pairing details from Android Settings")
        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("Phone IP / host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pairingPort,
            onValueChange = onPairingPortChange,
            label = { Text("Pairing port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pairingCode,
            onValueChange = onPairingCodeChange,
            label = { Text("Temporary 6-digit pairing code") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onPair,
            enabled = pairingCode.length == 6 && (pairingPort.toIntOrNull()?.let { it in 1..65535 } == true),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Pair device") }
        SectionHeading("2 · Connect", "Use the current connection port, not the pairing port")
        OutlinedTextField(
            value = connectionPort,
            onValueChange = onConnectionPortChange,
            label = { Text("Connection port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onConnect,
            enabled = connectionPort.toIntOrNull()?.let { it in 1..65535 } == true,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Connect + verify shell identity") }
        SectionHeading("3 · Helper and input probes", "Only fixed, bounded helper requests are exposed")
        ActionButton("Bootstrap helper + verify UID", onClick = onStartHelper)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onTap, modifier = Modifier.weight(1f)) { Text("Test tap") }
            OutlinedButton(onClick = onSwipe, modifier = Modifier.weight(1f)) { Text("Test swipe") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onStopHelper, modifier = Modifier.weight(1f)) { Text("Stop helper") }
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) { Text("Disconnect") }
        }
        OutlinedButton(onClick = onOpenTarget, modifier = Modifier.fillMaxWidth()) { Text("Open test target") }
        InfoNotice("Keep pairing code private", "The code field clears when Pair is pressed and is not saved in UI preferences or the Room history.")
    }
}

@Composable
private fun DiagnosticsScreen(
    helperStatus: String,
    adbStatus: String,
    recentRuns: List<ProbeRunEntity>,
    onOpenTarget: () -> Unit
) {
    ContentColumn {
        ScreenHeading("Diagnostics", "Evidence over assumptions", "Use these reports to decide what has actually been verified.")
        StatusDetailCard("Shizuku helper", helperStatus, Icons.Filled.Security)
        StatusDetailCard("Direct ADB", adbStatus, Icons.Filled.Wifi)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Evidence checklist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ChecklistItem("Activation state checked", "Binder/permission or Pair → Connect state")
                ChecklistItem("Helper identity checked", "UID or shell response recorded separately")
                ChecklistItem("Target event observed", "Tap/key/motion actually appears in the Test Target")
                ChecklistItem("Device result documented", "Model, Android version, date and redacted result")
                Button(onClick = onOpenTarget, modifier = Modifier.fillMaxWidth()) { Text("Open test target") }
            }
        }
        SectionHeading("Recent probe history", "Only fixed labels and outcomes are stored")
        if (recentRuns.isEmpty()) {
            EmptyState("No probe history yet", "Run a check or an input probe. The event history intentionally excludes pairing codes, private keys and raw command output.")
        } else {
            recentRuns.forEach { item -> HistoryRow(item) }
        }
        InfoNotice("A history entry is not an input PASS", "A tap or swipe is marked UNVERIFIED until the target app visibly records the expected event and the test is documented.")
    }
}

@Composable
private fun SettingsScreen(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    ContentColumn {
        ScreenHeading("Settings", "Make the Lab comfortable to use", "Appearance settings are saved locally on this device.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(if (darkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Dark theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (darkTheme) "Deep navy with teal accents" else "Soft neutral with teal accents", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = darkTheme, onCheckedChange = { onToggleTheme() })
            }
        }
        SectionHeading("Privacy", "Local-first feasibility testing")
        InfoNotice("No analytics", "No telemetry is included. The local history stores timestamps and fixed action/outcome labels only; it does not store pairing codes, ADB keys or command output.")
        SectionHeading("Platform", "Android 11 and newer")
        KeyValueLine("Minimum Android", "Android 11 · API 30")
        KeyValueLine("UI toolkit", "Jetpack Compose + Material 3")
        KeyValueLine("Navigation", "AndroidX Navigation Compose")
        KeyValueLine("Local preferences", "AndroidX DataStore")
        KeyValueLine("Probe history", "AndroidX Room · latest 100 entries")
    }
}

@Composable
private fun ContentColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
private fun ScreenHeading(title: String, eyebrow: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusCard(title: String, subtitle: String, status: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(15.dp), modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            StatusPill(status)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusDetailCard(title: String, status: String, icon: ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            StatusPill(status)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val lower = status.lowercase()
    val unverified = listOf("not verified", "not yet verified", "does not prove", "not an automatic", "injection is not", "input delivery", "experimental", "unverified").any { keyword -> lower.contains(keyword) }
    val failure = !unverified && listOf("failed", "denied", "unavailable", "not running", "cannot ", "error").any { keyword -> lower.contains(keyword) }
    val ready = !unverified && !failure && listOf("connected", "permission granted", "succeeded", "ready", "reports uid").any { keyword -> lower.contains(keyword) }
    val (label, container, foreground) = when {
        unverified -> Triple("UNVERIFIED", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        failure -> Triple("ACTION NEEDED", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        ready -> Triple("CHECK REPORTED", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        else -> Triple("STATUS", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(color = container, shape = CircleShape) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = foreground, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionGrid(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit, secondary: Boolean = false) {
    if (secondary) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    } else {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}

@Composable
private fun InfoNotice(title: String, message: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChecklistItem(title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryRow(item: ProbeRunEntity) {
    val whenText = remember(item.occurredAtMillis) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.occurredAtMillis))
    }
    val outcomeLabel = item.outcome.replace('_', ' ')
    val outcomeColor = when (item.outcome) {
        "SUCCEEDED" -> MaterialTheme.colorScheme.primary
        "FAILED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(outcomeColor))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.action.replace('_', ' '), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("${item.provider.replace('_', ' ')} · $whenText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(outcomeLabel, style = MaterialTheme.typography.labelSmall, color = outcomeColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun KeyValueLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}
