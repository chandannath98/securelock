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
import android.widget.Toast
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

    // --- APP LAUNCH PROTECTION ---
    // If setup is done, we must authenticate first.
    // If setup is NOT done, we go to permissions/setup.
    val startDest = if (isSetupDone) "auth" else "permissions"

    NavHost(navController = navController, startDestination = startDest) {
        composable("permissions") { PermissionScreen(navController) }
        composable("auth") { AuthScreen(navController) }
        composable("lock_type") { LockTypeScreen(navController) }
        composable("pin_setup") { PinSetupScreen(navController, isChangeMode = false) }
        composable("change_pin") { PinSetupScreen(navController, isChangeMode = true) }
        composable("app_list") { AppListScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
    }
}

// --- NEW: Authentication Screen (App Launch) ---
@Composable
fun AuthScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val prefs = context.getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE)
    val savedPin = prefs.getString("USER_PIN", "")
    val isBiometricEnabled = prefs.getBoolean("BIOMETRIC_ENABLED", true)

    var pin by remember { mutableStateOf("") }

    // Navigate to App List if successful
    fun onAuthSuccess() {
        // Check permissions after auth
        if (areAllPermissionsGranted(context)) {
            navController.navigate("app_list") { popUpTo(0) }
        } else {
            navController.navigate("permissions") { popUpTo(0) }
        }
    }

    // Trigger Biometric on load
    LaunchedEffect(Unit) {
        if (isBiometricEnabled && activity != null && BiometricHelper.isBiometricAvailable(context)) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = { onAuthSuccess() },
                onError = { /* Stay on PIN screen */ }
            )
        }
    }

    // PIN Check Logic
    LaunchedEffect(pin) {
        if (pin == savedPin) {
            onAuthSuccess()
        } else if (pin.length == 4) {
            pin = "" // Wrong PIN, reset
        }
    }

    // Reuse the PIN UI layout
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).background(Color(0xFFF5F7FA)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("SecureLock", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Enter PIN to access settings", color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.Center) {
            repeat(4) { index ->
                Box(modifier = Modifier.padding(8.dp).size(20.dp).background(if (index < pin.length) MaterialTheme.colorScheme.primary else Color.LightGray, CircleShape))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Keypad (Reusing logic)
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
                // If setup done, go to App List (Auth already happened or not needed here)
                navController.navigate("app_list") { popUpTo(0) }
            } else {
                navController.navigate("lock_type")
            }
        }
    }

    // Observer for Resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkTrigger++
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
        Icon(Icons.Default.Security, "Logo", modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Permissions Required", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("SecureLock needs these to function.", color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        PermissionItem("Usage Access", "Detect running apps", granted = isUsageGranted(context)) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        PermissionItem("Overlay Permissions", "Show lock screen", granted = Settings.canDrawOverlays(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }

        PermissionItem("Accessibility Service", "Detect app launch (Critical)", granted = isAccessibilityGranted(context)) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = { }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = false) {
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
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() }.background(Color.White, RoundedCornerShape(8.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.Settings, null, tint = if (granted) Color.Green else Color.Gray)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(desc, fontSize = 12.sp, color = Color.Gray) }
        if (!granted) Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
    }
}

// --- Lock Type & PIN Setup (Standard) ---
@Composable
fun LockTypeScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Choose Your Lock Type", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        LockTypeCard(Icons.Default.Pin, "PIN Code", "4-digit custom PIN") { navController.navigate("pin_setup") }
        LockTypeCard(Icons.Default.GridOn, "Pattern Lock", "Connect dots") { navController.navigate("pin_setup") }
        LockTypeCard(Icons.Default.Fingerprint, "Fingerprint/Face ID", "Quick & secure") { navController.navigate("pin_setup") }
    }
}
@Composable
fun LockTypeCard(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column { Text(title, fontWeight = FontWeight.Bold); Text(sub, fontSize = 12.sp, color = Color.Gray) }
        }
    }
}

@Composable
fun PinSetupScreen(navController: NavController, isChangeMode: Boolean = false) {
    var pin by remember { mutableStateOf("") }
    val context = LocalContext.current

    val titleText = if (isChangeMode) "Enter New PIN" else "Set Your PIN Code"
    val buttonText = if (isChangeMode) "Update PIN" else "Confirm & Continue"

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(titleText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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

        Button(
            onClick = {
                if (pin.length == 4) {
                    context.getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE).edit().apply {
                        putString("USER_PIN", pin)
                        putBoolean("IS_SETUP_DONE", true)
                        apply()
                    }
                    if (isChangeMode) {
                        Toast.makeText(context, "PIN Updated", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    } else {
                        navController.navigate("app_list") { popUpTo(0) }
                    }
                }
            },
            enabled = pin.length == 4,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(buttonText)
        }
    }
}

// --- App List (Unchanged mostly) ---
data class AppInfo(val name: String, val packageName: String, val icon: ImageBitmap?, var isLocked: Boolean, val isSystem: Boolean)

@Composable
fun AppListScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE)
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

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

    // Filter Logic combining Type and Search Query
    val displayedApps = remember(allApps, selectedFilter, searchQuery) {
        val typeFiltered = when(selectedFilter) {
            "System" -> allApps.filter { it.isSystem }
            "User" -> allApps.filter { !it.isSystem }
            else -> allApps
        }
        if (searchQuery.isBlank()) {
            typeFiltered
        } else {
            typeFiltered.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {
        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Protect Your Apps", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { navController.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            placeholder = { Text("Search apps...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Row(modifier = Modifier.padding(16.dp)) {
            FilterChip(selectedFilter == "All", "All Apps") { selectedFilter = "All" }
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(selectedFilter == "User", "User Apps") { selectedFilter = "User" }
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(selectedFilter == "System", "System") { selectedFilter = "System" }
        }
        Button(onClick = {
            val lockedSet = prefs.getStringSet("LOCKED_APPS", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            displayedApps.forEach { lockedSet.add(it.packageName) }
            prefs.edit().putStringSet("LOCKED_APPS", lockedSet).apply()
            allApps = allApps.map { if (displayedApps.any { da -> da.packageName == it.packageName }) it.copy(isLocked = true) else it }
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Lock All visible") }

        if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(displayedApps) { app ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (app.icon != null) Image(bitmap = app.icon, null, modifier = Modifier.size(40.dp)) else Box(Modifier.size(40.dp).background(Color.LightGray, CircleShape))
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
    SuggestionChip(onClick = onClick, label = { Text(label) }, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = if (selected) Color(0xFFE3F2FD) else Color.Transparent, labelColor = if (selected) primaryColor else Color.Gray),)
}

fun Drawable.toBitmap(): ImageBitmap {
    val bitmap = if (this is BitmapDrawable && this.bitmap != null) this.bitmap else {
        val bmp = Bitmap.createBitmap(if (intrinsicWidth > 0) intrinsicWidth else 1, if (intrinsicHeight > 0) intrinsicHeight else 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp); setBounds(0, 0, canvas.width, canvas.height); draw(canvas); bmp
    }
    return bitmap.asImageBitmap()
}

// --- 5. Updated Settings Screen with Biometric Toggle ---
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("SecureLockPrefs", Context.MODE_PRIVATE)

    var currentMode by remember { mutableStateOf(prefs.getInt("LOCK_TIMEOUT_MODE", 0)) }
    var biometricEnabled by remember { mutableStateOf(prefs.getBoolean("BIOMETRIC_ENABLED", true)) }

    fun saveMode(mode: Int) {
        currentMode = mode
        prefs.edit().putInt("LOCK_TIMEOUT_MODE", mode).apply()
    }

    fun toggleBiometric(enabled: Boolean) {
        biometricEnabled = enabled
        prefs.edit().putBoolean("BIOMETRIC_ENABLED", enabled).apply()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {
        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Biometric Toggle
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Enable Biometrics", fontWeight = FontWeight.SemiBold)
                    Text("Use Fingerprint/Face to unlock", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(checked = biometricEnabled, onCheckedChange = { toggleBiometric(it) })
            }

            // Security Section
            Text("Security", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))
            SettingsAction(
                title = "Change PIN",
                desc = "Update your 4-digit access code.",
                icon = Icons.Default.LockReset
            ) {
                navController.navigate("change_pin")
            }

            Text("Re-lock Policy", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))
            SettingsOption("Instantly", "Locks immediately after leaving the app.", selected = currentMode == 0) { saveMode(0) }
            SettingsOption("After Screen Lock", "Apps stay unlocked until phone lock.", selected = currentMode == 1) { saveMode(1) }
            SettingsOption("After 10 Minutes", "Re-locks apps if unused for 10 mins.", selected = currentMode == 2) { saveMode(2) }
        }
    }
}

@Composable
fun SettingsOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color.White, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(16.dp))
        Column { Text(title, fontWeight = FontWeight.SemiBold); Text(desc, fontSize = 12.sp, color = Color.Gray) }
    }
}

@Composable
fun SettingsAction(title: String, desc: String, icon: ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color.White, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(desc, fontSize = 12.sp, color = Color.Gray) }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}