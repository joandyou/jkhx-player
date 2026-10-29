package com.jkhx.player.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import com.jkhx.player.update.AppUpdateManager
import com.jkhx.player.update.AppReleaseInfo
import com.jkhx.player.update.UpdateCheckResult
import com.jkhx.player.update.DownloadProgress
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jkhx.player.JkhxApplication
import com.jkhx.player.R
import com.jkhx.player.settings.AppLanguage
import com.jkhx.player.settings.AppThemeMode
import com.jkhx.player.settings.DeviceDisplayMode
import com.jkhx.player.settings.LocaleHelper
import com.jkhx.player.settings.ProxyType
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.ShapeTokens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Sync
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.jkhx.player.sync.GoogleAuthManager
import com.jkhx.player.sync.SyncManager
import com.jkhx.player.sync.SyncState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAuxServices: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsManager = JkhxApplication.instance.settingsManager
    val themeMode by settingsManager.themeMode.collectAsState()
    val displayMode by settingsManager.displayMode.collectAsState()
    val currentLanguage by settingsManager.language.collectAsState()
    val adultMode by settingsManager.isAdultEnabled.collectAsState()
    val comicMode by settingsManager.isComicEnabled.collectAsState()
    val novelMode by settingsManager.isNovelEnabled.collectAsState()
    val liveMode by settingsManager.isLiveEnabled.collectAsState()

    val authManager = remember { GoogleAuthManager.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }
    val syncState by syncManager.syncState.collectAsState()

    val googleEmail by settingsManager.googleAccountEmail.collectAsState()
    val googleName by settingsManager.googleAccountName.collectAsState()
    val googlePhoto by settingsManager.googleAccountPhoto.collectAsState()
    val isAutoSync by settingsManager.isAutoSyncEnabled.collectAsState()
    val lastSyncTime by settingsManager.lastSyncTimestamp.collectAsState()

    val subtitleManager = remember { JkhxApplication.instance.subtitleManager }
    var cacheSizeBytes by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            cacheSizeBytes = subtitleManager.cacheManager.getTotalAppCacheBytes()
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes.toFloat() / (1024f * 1024f * 1024f))
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes.toFloat() / (1024f * 1024f))
            bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes.toFloat() / 1024f)
            else -> "$bytes B"
        }
    }

    var showSignOutDialog by remember { mutableStateOf(false) }

    val appUpdateManager = remember { AppUpdateManager.getInstance(context) }
    val updateCheckState by appUpdateManager.checkState.collectAsState()
    val downloadProgress by appUpdateManager.downloadProgress.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var activeReleaseInfo by remember { mutableStateOf<AppReleaseInfo?>(null) }

    LaunchedEffect(updateCheckState) {
        when (val state = updateCheckState) {
            is UpdateCheckResult.UpdateAvailable -> {
                activeReleaseInfo = state.info
                showUpdateDialog = true
            }
            is UpdateCheckResult.UpToDate -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_update_already_latest, state.currentVersion),
                    Toast.LENGTH_SHORT
                ).show()
                appUpdateManager.resetCheckState()
            }
            is UpdateCheckResult.Error -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_update_error, state.message),
                    Toast.LENGTH_LONG
                ).show()
                appUpdateManager.resetCheckState()
            }
            else -> {}
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                authManager.handleSignInSuccess(account)
                Toast.makeText(context, context.getString(R.string.settings_sync_success_toast), Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    syncManager.syncNow(force = true)
                }
            }
        } catch (e: ApiException) {
            if (e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                val detailMsg = when (e.statusCode) {
                    CommonStatusCodes.DEVELOPER_ERROR -> "Code 10 (DEVELOPER_ERROR: Cần cấu hình đúng mã SHA-1 của app trên Google Cloud Console)"
                    CommonStatusCodes.NETWORK_ERROR -> "Code 7 (NETWORK_ERROR: Lỗi kết nối mạng)"
                    CommonStatusCodes.INTERNAL_ERROR -> "Code 8 (INTERNAL_ERROR: Lỗi hệ thống Google Play Services)"
                    else -> "Code ${e.statusCode}"
                }
                Toast.makeText(context, context.getString(R.string.settings_sync_sign_in_failed, detailMsg), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.settings_sync_sign_in_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = AppTheme.dimens.space16,
                    end = AppTheme.dimens.space16,
                    top = AppTheme.dimens.space12,
                    bottom = 100.dp
                )
        ) {
            // 1. GIAO DIỆN & HỆ THỐNG
            SettingsSectionHeader(stringResource(R.string.settings_theme_section))
            SettingsCard {
                // Theme Selection
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_theme_section),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            SettingPill(
                                label = stringResource(mode.titleRes),
                                isSelected = themeMode == mode,
                                onClick = { settingsManager.setThemeMode(mode) }
                            )
                        }
                    }
                }

                SettingsCardDivider()

                // Display Mode Selection
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_display_mode_section),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_display_mode_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DeviceDisplayMode.entries.forEach { mode ->
                            SettingPill(
                                label = stringResource(mode.titleRes),
                                isSelected = displayMode == mode,
                                onClick = { settingsManager.setDisplayMode(mode) }
                            )
                        }
                    }
                }

                SettingsCardDivider()

                // Language Selection
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_language_section),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_language_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            SettingPill(
                                label = stringResource(lang.titleRes),
                                isSelected = currentLanguage == lang,
                                onClick = {
                                    if (currentLanguage != lang) {
                                        settingsManager.setLanguage(lang)
                                        LocaleHelper.updateConfiguration(context, lang.code)
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. TÀI KHOẢN & ĐỒNG BỘ ĐÁM MÂY
            SettingsSectionHeader(stringResource(R.string.settings_sync_section))
            SettingsCard {
                if (googleEmail.isNullOrBlank()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_sync_desc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                signInLauncher.launch(authManager.signInIntent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.settings_sync_sign_in_google),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!googlePhoto.isNullOrBlank()) {
                            AsyncImage(
                                model = googlePhoto,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (googleName ?: googleEmail ?: "G").take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = googleName ?: stringResource(R.string.settings_sync_account_google),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = googleEmail ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = { showSignOutDialog = true }
                        ) {
                            Text(
                                text = stringResource(R.string.settings_sync_sign_out),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    SettingsCardDivider()

                    // Auto sync switch
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_sync_auto_title),
                        subtitle = stringResource(R.string.settings_sync_auto_desc),
                        checked = isAutoSync,
                        onCheckedChange = { settingsManager.setAutoSyncEnabled(it) }
                    )

                    SettingsCardDivider()

                    // Last sync time & Sync Now button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lastSyncFormatted = if (lastSyncTime > 0) {
                            val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
                            sdf.format(Date(lastSyncTime))
                        } else {
                            stringResource(R.string.settings_sync_never)
                        }

                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_sync_last_time, lastSyncFormatted),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (syncState is SyncState.Error) {
                                Text(
                                    text = "✕ ${(syncState as SyncState.Error).message}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (syncState is SyncState.Success) {
                                Text(
                                    text = "✓ " + stringResource(R.string.settings_sync_success_toast),
                                    fontSize = 11.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    syncManager.syncNow(force = true)
                                }
                            },
                            enabled = syncState !is SyncState.Syncing,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (syncState is SyncState.Syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.settings_sync_syncing),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.settings_sync_now_btn),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            if (showSignOutDialog) {
                AlertDialog(
                    onDismissRequest = { showSignOutDialog = false },
                    title = { Text(stringResource(R.string.settings_sync_sign_out_confirm_title), fontWeight = FontWeight.Bold) },
                    text = { Text(stringResource(R.string.settings_sync_sign_out_confirm_desc)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSignOutDialog = false
                                authManager.signOut()
                            }
                        ) {
                            Text(stringResource(R.string.settings_sync_sign_out), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSignOutDialog = false }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. NỘI DUNG & BỘ LỌC
            SettingsSectionHeader(stringResource(R.string.settings_adult_section))
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_adult_title),
                    subtitle = if (adultMode) stringResource(R.string.settings_adult_on) else stringResource(R.string.settings_adult_off),
                    checked = adultMode,
                    onCheckedChange = { settingsManager.setAdultEnabled(it) }
                )
                SettingsCardDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_comic_title),
                    subtitle = if (comicMode) stringResource(R.string.settings_comic_on) else stringResource(R.string.settings_comic_off),
                    checked = comicMode,
                    onCheckedChange = { settingsManager.setComicEnabled(it) }
                )
                SettingsCardDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_novel_title),
                    subtitle = if (novelMode) stringResource(R.string.settings_novel_on) else stringResource(R.string.settings_novel_off),
                    checked = novelMode,
                    onCheckedChange = { settingsManager.setNovelEnabled(it) }
                )
                SettingsCardDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_live_title),
                    subtitle = if (liveMode) stringResource(R.string.settings_live_on) else stringResource(R.string.settings_live_off),
                    checked = liveMode,
                    onCheckedChange = { settingsManager.setLiveEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. TÙY CHỈNH NÂNG CAO
            SettingsSectionHeader(stringResource(R.string.settings_aux_services_title))
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToAuxServices)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_aux_services_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_aux_services_subtitle),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. MẠNG & PROXY TÙY CHỈNH
            val isProxyEnabled by settingsManager.isCustomProxyEnabled.collectAsState()
            val savedProxyType by settingsManager.customProxyType.collectAsState()
            val savedProxyHost by settingsManager.customProxyHost.collectAsState()
            val savedProxyPort by settingsManager.customProxyPort.collectAsState()

            var proxyHostInput by remember(savedProxyHost) { mutableStateOf(savedProxyHost) }
            var proxyPortInput by remember(savedProxyPort) { mutableStateOf(if (savedProxyPort > 0) savedProxyPort.toString() else "1080") }
            var selectedProxyType by remember(savedProxyType) { mutableStateOf(savedProxyType) }

            var isTestingProxy by remember { mutableStateOf(false) }
            var proxyTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

            SettingsSectionHeader(stringResource(R.string.settings_proxy_section))
            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_proxy_enable_title),
                    subtitle = if (isProxyEnabled) stringResource(R.string.settings_proxy_enable_on) else stringResource(R.string.settings_proxy_desc),
                    checked = isProxyEnabled,
                    onCheckedChange = { settingsManager.setCustomProxyEnabled(it) }
                )

                if (isProxyEnabled) {
                    SettingsCardDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_proxy_type_label),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProxyType.entries.forEach { pType ->
                                SettingPill(
                                    label = pType.title,
                                    isSelected = selectedProxyType == pType,
                                    onClick = {
                                        selectedProxyType = pType
                                        settingsManager.setCustomProxyType(pType)
                                        proxyTestResult = null
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = proxyHostInput,
                                onValueChange = {
                                    proxyHostInput = it
                                    proxyTestResult = null
                                },
                                label = { Text(stringResource(R.string.settings_proxy_host_label)) },
                                placeholder = { Text(stringResource(R.string.settings_proxy_host_placeholder)) },
                                modifier = Modifier.weight(0.68f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                )
                            )

                            OutlinedTextField(
                                value = proxyPortInput,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }
                                    proxyPortInput = filtered
                                    proxyTestResult = null
                                },
                                label = { Text(stringResource(R.string.settings_proxy_port_label)) },
                                placeholder = { Text(stringResource(R.string.settings_proxy_port_placeholder)) },
                                modifier = Modifier.weight(0.32f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val hostToSave = proxyHostInput.trim()
                                    val portToSave = proxyPortInput.toIntOrNull() ?: 1080
                                    settingsManager.setCustomProxyHost(hostToSave)
                                    settingsManager.setCustomProxyPort(portToSave)
                                    settingsManager.setCustomProxyType(selectedProxyType)
                                    Toast.makeText(context, context.getString(R.string.settings_proxy_saved_toast), Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.settings_proxy_save_btn), fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isTestingProxy = true
                                        proxyTestResult = null
                                        val hostToTest = proxyHostInput.trim().ifBlank { savedProxyHost }
                                        val portToTest = proxyPortInput.toIntOrNull() ?: savedProxyPort
                                        val res = settingsManager.testProxyConnection(hostToTest, portToTest, selectedProxyType)
                                        isTestingProxy = false
                                        proxyTestResult = res
                                    }
                                },
                                enabled = !isTestingProxy,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                if (isTestingProxy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.settings_proxy_testing), fontSize = 13.sp)
                                } else {
                                    Text(stringResource(R.string.settings_proxy_test_btn), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }

                        proxyTestResult?.let { (success, msg) ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (success) "✓ $msg" else "✕ $msg",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. QUẢN LÝ BỘ NHỚ ĐỆM
            SettingsSectionHeader(stringResource(R.string.settings_cache_section_title))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_cache_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_cache_size_label, formatBytes(cacheSizeBytes)),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    subtitleManager.cacheManager.clearAllAppCache()
                                    cacheSizeBytes = subtitleManager.cacheManager.getTotalAppCacheBytes()
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.settings_cache_cleared_toast), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_cache_clear_btn), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 7. THÔNG TIN ỨNG DỤNG
            SettingsSectionHeader(stringResource(R.string.settings_app_info_section))
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo),
                        contentDescription = stringResource(R.string.home_logo_desc),
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            "JKHX PLAYER",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.app_subtitle),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SettingsCardDivider()

                Column(modifier = Modifier.padding(16.dp)) {
                    val appVersionName = remember(context) {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.12"
                        } catch (_: Exception) {
                            "1.0.12"
                        }
                    }

                    AppInfoRow(
                        label = stringResource(R.string.settings_app_name_info),
                        value = "JKHX Player"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppInfoRow(
                        label = stringResource(R.string.settings_version_info, "").trim().removeSuffix(":"),
                        value = appVersionName
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppInfoRow(
                        label = stringResource(R.string.settings_arch_info).substringBefore(":"),
                        value = stringResource(R.string.settings_arch_info).substringAfter(":").trim()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppInfoRow(
                        label = stringResource(R.string.settings_theme_section),
                        value = stringResource(themeMode.titleRes)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppInfoRow(
                        label = stringResource(R.string.settings_display_mode_section),
                        value = stringResource(displayMode.titleRes)
                    )
                }

                SettingsCardDivider()

                Surface(
                    onClick = {
                        if (updateCheckState !is UpdateCheckResult.Checking) {
                            coroutineScope.launch {
                                appUpdateManager.checkForUpdates()
                            }
                        }
                    },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (updateCheckState is UpdateCheckResult.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_check_update_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (val state = updateCheckState) {
                                    is UpdateCheckResult.Checking -> stringResource(R.string.settings_checking_update)
                                    is UpdateCheckResult.UpdateAvailable -> stringResource(
                                        R.string.settings_update_available_msg,
                                        state.info.versionName,
                                        state.currentVersion
                                    )
                                    else -> stringResource(R.string.settings_check_update_desc)
                                },
                                fontSize = 12.sp,
                                color = if (updateCheckState is UpdateCheckResult.UpdateAvailable)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                lineHeight = 16.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (showUpdateDialog && activeReleaseInfo != null) {
                val info = activeReleaseInfo!!
                AlertDialog(
                    onDismissRequest = {
                        if (downloadProgress !is DownloadProgress.Downloading) {
                            showUpdateDialog = false
                            appUpdateManager.resetDownloadProgress()
                            appUpdateManager.resetCheckState()
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_update_available_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.settings_update_available_msg,
                                    info.versionName,
                                    appUpdateManager.currentVersionName
                                ),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (info.releaseNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.settings_update_changelog_title),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = info.releaseNotes,
                                        fontSize = 12.5.sp,
                                        lineHeight = 17.sp,
                                        modifier = Modifier.padding(10.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            when (val progress = downloadProgress) {
                                is DownloadProgress.Downloading -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        LinearProgressIndicator(
                                            progress = { progress.percent / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.settings_update_downloading, progress.percent),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                is DownloadProgress.Failure -> {
                                    Text(
                                        text = stringResource(R.string.settings_update_download_failed, progress.error),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                is DownloadProgress.Success -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Đã tải xong APK. Sẵn sàng cài đặt!",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                else -> {
                                    if (info.apkSizeBytes > 0) {
                                        Text(
                                            text = "Dung lượng APK: ${formatBytes(info.apkSizeBytes)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        when (val progress = downloadProgress) {
                            is DownloadProgress.Success -> {
                                Button(
                                    onClick = {
                                        appUpdateManager.launchInstall(context, progress.apkFile)
                                    }
                                ) {
                                    Text(stringResource(R.string.settings_update_install))
                                }
                            }
                            is DownloadProgress.Downloading -> {
                                Button(
                                    onClick = {},
                                    enabled = false
                                ) {
                                    Text(stringResource(R.string.settings_update_downloading, progress.percent))
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val result = appUpdateManager.downloadAndInstall(info)
                                            result.onSuccess { file ->
                                                appUpdateManager.launchInstall(context, file)
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.settings_update_btn_download))
                                }
                            }
                        }
                    },
                    dismissButton = {
                        if (downloadProgress !is DownloadProgress.Downloading) {
                            TextButton(
                                onClick = {
                                    showUpdateDialog = false
                                    appUpdateManager.resetDownloadProgress()
                                    appUpdateManager.resetCheckState()
                                }
                            ) {
                                Text(stringResource(R.string.settings_update_btn_later))
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun SettingsCardDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AppInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SettingPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        shape = ShapeTokens.Card,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(horizontal = AppTheme.dimens.space20, vertical = AppTheme.dimens.space10),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                maxLines = 1
            )
        }
    }
}
