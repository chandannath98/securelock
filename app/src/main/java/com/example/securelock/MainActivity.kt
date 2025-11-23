package com.example.securelock

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2196F3),
                    onPrimary = Color.White,
                    background = Color(0xFFF5F7FA)
                )
            ) {
                SecureLockApp()
            }
        }
    }
}

@Composable
fun SecureLockApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val prefs = context.getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE)
    val isSetupDone = prefs.getBoolean("IS_SETUP_DONE", false)

    // --- PERMISSION CHECK ON APP OPEN ---
    // Check permissions every time the app comes to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!areAllPermissionsGranted(context)) {
                    // If setup was done but permissions revoked, go to permissions
                    // If setup not done, we are likely already there or heading there
                    if (navController.currentDestination?.route != "permissions") {
                        navController.navigate("permissions") {
                            popUpTo(0)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val startDest = if (isSetupDone && areAllPermissionsGranted(context)) "app_list" else "permissions"

    NavHost(navController = navController, startDestination = startDest) {
        composable("permissions") { PermissionScreen(navController) }
        composable("lock_type") { LockTypeScreen(navController) }
        composable("pin_setup") { PinSetupScreen(navController) }
        composable("app_list") { AppListScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
    }
}

// --- Permission Helper Logic ---
fun areAllPermissionsGranted(context: Context): Boolean {
    // 1. Overlay Permission
    if (!Settings.canDrawOverlays(context)) return false

    // 2. Usage Stats
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    if (mode != AppOpsManager.MODE_ALLOWED) return false

    // 3. Accessibility Service (Basic Check)
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}

// --- 1. Permissions Screen ---
@Composable
fun PermissionScreen(navController: NavController) {
    val context = LocalContext.current

    // Re-check trigger
    var checkTrigger by remember { mutableStateOf(0) }
    val allGranted = remember(checkTrigger) { areAllPermissionsGranted(context) }

    // Auto-advance if all granted
    LaunchedEffect(allGranted) {
        if (allGranted) {
            val prefs = context.getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("IS_SETUP_DONE", false)) {
                navController.navigate("app_list") { popUpTo(0) }
            } else {
                navController.navigate("lock_type")
            }
        }
    }

    // Observer for Resume to update UI state when user comes back from Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkTrigger++ // Force re-composition to check permissions
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "Logo",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Permissions Required", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("SecureLock needs these to function.", color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        PermissionItem("Usage Access", "Detect running apps",
            granted = isUsageGranted(context)) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        PermissionItem("Overlay Permissions", "Show lock screen",
            granted = Settings.canDrawOverlays(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }

        PermissionItem("Accessibility Service", "Detect app launch (Critical)",
            granted = isAccessibilityGranted(context)) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { /* Handled by auto-check */ },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = false // Button is just visual, navigation happens automatically
        ) {
            Text(if (allGranted) "Redirecting..." else "Grant All to Continue")
        }
    }
}

fun isUsageGranted(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun isAccessibilityGranted(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}

@Composable
fun PermissionItem(title: String, desc: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Settings,
            contentDescription = null,
            tint = if (granted) Color.Green else Color.Gray
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 12.sp, color = Color.Gray)
        }
        if (!granted) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}

// --- 2. Lock Type Selection (Unchanged) ---
@Composable
fun LockTypeScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Choose Your Lock Type", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        LockTypeCard(Icons.Default.Pin, "PIN Code", "4-digit custom PIN") { navController.navigate("pin_setup") }
        LockTypeCard(Icons.Default.GridOn, "Pattern Lock", "Connect dots (Uses PIN)") { navController.navigate("pin_setup") }
        LockTypeCard(Icons.Default.Fingerprint, "Fingerprint/Face ID", "Quick & secure") { navController.navigate("pin_setup") }
    }
}
@Composable
fun LockTypeCard(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column { Text(title, fontWeight = FontWeight.Bold); Text(sub, fontSize = 12.sp, color = Color.Gray) }
        }
    }
}

// --- 3. PIN Setup (Unchanged) ---
@Composable
fun PinSetupScreen(navController: NavController) {
    var pin by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Set Your PIN Code", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.Center) { repeat(4) { index -> Box(modifier = Modifier.padding(8.dp).size(20.dp).background(if (index < pin.length) MaterialTheme.colorScheme.primary else Color.LightGray, CircleShape)) } }
        Spacer(modifier = Modifier.height(48.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "Del")
        LazyColumn(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            items(keys.chunked(3)) { rowKeys ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rowKeys.forEach { key ->
                        Box(modifier = Modifier.padding(8.dp).size(80.dp).clip(CircleShape).clickable { if (key == "Del") { if (pin.isNotEmpty()) pin = pin.dropLast(1) } else if (key.isNotEmpty()) { if (pin.length < 4) pin += key } }, contentAlignment = Alignment.Center) {
                            if (key == "Del") Icon(Icons.Default.Backspace, null) else Text(key, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = { if (pin.length == 4) { context.getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE).edit().apply { putString("USER_PIN", pin); putBoolean("IS_SETUP_DONE", true); apply() }; navController.navigate("app_list") { popUpTo(0) } } }, enabled = pin.length == 4, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Confirm & Continue") }
    }
}

// --- 4. App List (Home) ---
data class AppInfo(val name: String, val packageName: String, val icon: ImageBitmap?, var isLocked: Boolean, val isSystem: Boolean)

@Composable
fun AppListScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE)
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            val list = packages.mapNotNull { pack ->
                if (pack.packageName == context.packageName) return@mapNotNull null
                val appInfo = pack.applicationInfo ?: return@mapNotNull null
                if (pm.getLaunchIntentForPackage(pack.packageName) == null) return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = appInfo.loadLabel(pm).toString()
                val iconDrawable = appInfo.loadIcon(pm)
                val iconBitmap = iconDrawable.toBitmap()
                AppInfo(label, pack.packageName, iconBitmap, false, isSystem)
            }.sortedBy { it.name }
            val lockedSet = prefs.getStringSet("LOCKED_APPS", emptySet()) ?: emptySet()
            allApps = list.map { it.copy(isLocked = lockedSet.contains(it.packageName)) }
            isLoading = false
        }
    }

    fun toggleLock(app: AppInfo) {
        val newStatus = !app.isLocked
        val lockedSet = prefs.getStringSet("LOCKED_APPS", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        if (newStatus) lockedSet.add(app.packageName) else lockedSet.remove(app.packageName)
        prefs.edit().putStringSet("LOCKED_APPS", lockedSet).apply()
        allApps = allApps.map { if (it.packageName == app.packageName) it.copy(isLocked = newStatus) else it }
    }

    val displayedApps = when(selectedFilter) {
        "System" -> allApps.filter { it.isSystem }
        "User" -> allApps.filter { !it.isSystem }
        else -> allApps
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {
        // Header with Settings Button
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Protect Your Apps", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { navController.navigate("settings") }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        Row(modifier = Modifier.padding(16.dp)) {
            FilterChip(selectedFilter == "All", "All Apps") { selectedFilter = "All" }
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(selectedFilter == "User", "User Apps") { selectedFilter = "User" }
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(selectedFilter == "System", "System") { selectedFilter = "System" }
        }

        Button(
            onClick = {
                val lockedSet = prefs.getStringSet("LOCKED_APPS", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                displayedApps.forEach { lockedSet.add(it.packageName) }
                prefs.edit().putStringSet("LOCKED_APPS", lockedSet).apply()
                allApps = allApps.map { if (displayedApps.any { da -> da.packageName == it.packageName }) it.copy(isLocked = true) else it }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("Lock All visible") }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(displayedApps) { app ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (app.icon != null) Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        else Box(Modifier.size(40.dp).background(Color.LightGray, CircleShape))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) { Text(app.name, fontWeight = FontWeight.Medium); if (app.isSystem) Text("System", fontSize = 10.sp, color = Color.Gray) }
                        Switch(checked = app.isLocked, onCheckedChange = { toggleLock(app) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val grayColor = Color.LightGray
    SuggestionChip(
        onClick = onClick,
        label = { Text(label) },
        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = if (selected) Color(0xFFE3F2FD) else Color.Transparent, labelColor = if (selected) primaryColor else Color.Gray),
//        border = SuggestionChipDefaults.suggestionChipBorder(borderColor = if (selected) primaryColor else grayColor)
    )
}

fun Drawable.toBitmap(): ImageBitmap {
    val bitmap = if (this is BitmapDrawable && this.bitmap != null) this.bitmap else {
        val bmp = Bitmap.createBitmap(if (intrinsicWidth > 0) intrinsicWidth else 1, if (intrinsicHeight > 0) intrinsicHeight else 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp); setBounds(0, 0, canvas.width, canvas.height); draw(canvas); bmp
    }
    return bitmap.asImageBitmap()
}

// --- 5. Settings Screen (New) ---
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE)

    // Modes: 0 = Instant, 1 = Screen Off, 2 = 10 Mins
    var currentMode by remember { mutableStateOf(prefs.getInt("LOCK_TIMEOUT_MODE", 0)) }

    fun saveMode(mode: Int) {
        currentMode = mode
        prefs.edit().putInt("LOCK_TIMEOUT_MODE", mode).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Re-lock Policy", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))

            SettingsOption(
                title = "Instantly",
                desc = "Locks immediately after leaving the app.",
                selected = currentMode == 0,
                onClick = { saveMode(0) }
            )

            SettingsOption(
                title = "After Screen Lock",
                desc = "Apps stay unlocked until you lock your phone.",
                selected = currentMode == 1,
                onClick = { saveMode(1) }
            )

            SettingsOption(
                title = "After 10 Minutes",
                desc = "Re-locks apps if unused for 10 minutes.",
                selected = currentMode == 2,
                onClick = { saveMode(2) }
            )
        }
    }
}

@Composable
fun SettingsOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 12.sp, color = Color.Gray)
        }
    }
}