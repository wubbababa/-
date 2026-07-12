package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.SessionRepository
import com.example.data.UsageSession
import com.example.ui.MainViewModel
import com.example.ui.theme.MinimalBg
import com.example.ui.theme.MinimalBlueDark
import com.example.ui.theme.MinimalBlueLight
import com.example.ui.theme.MinimalPurpleDark
import com.example.ui.theme.MinimalPurpleLight
import com.example.ui.theme.MinimalPurplePrimary
import com.example.ui.theme.MinimalTextMain
import com.example.ui.theme.MinimalTextMuted
import com.example.ui.theme.MinimalTileBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TomatoRed

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = SessionRepository(database.usageSessionDao())
        val factory = MainViewModel.Factory(application, repository)

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel(factory = factory)
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val countdown by viewModel.currentCountdown.collectAsState()
    val selectedDelay by viewModel.selectedDelaySeconds.collectAsState()
    val activeSeconds by viewModel.activeSessionSeconds.collectAsState()
    val stats by viewModel.statsState.collectAsState()
    val history by viewModel.allSessions.collectAsState()

    // Permissions Local State
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var hasOverlayPermission by remember { mutableStateOf(true) }

    // Helper to refresh permission statuses
    fun checkPermissions() {
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    // Refresh permissions on launch and resume
    LaunchedEffect(Unit) {
        checkPermissions()
    }

    // Notification permission launcher
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MinimalBg),
        containerColor = MinimalBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Digital Wellness Minimal Header Block
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .statusBarsPadding()
                        .testTag("app_top_bar")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DIGITAL WELLNESS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalTextMuted,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MinimalTextMain
                            )
                        }

                        // Cleansed Settings / Clear History Action Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MinimalPurpleLight)
                                .clickable {
                                    if (history.isNotEmpty()) {
                                        viewModel.clearHistory()
                                    }
                                }
                                .testTag("clear_history_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear History",
                                tint = MinimalPurpleDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 2. Main Countdown Circle Monitor Block
            item {
                MinimalMonitorDial(
                    isRunning = isRunning,
                    countdown = countdown,
                    totalDelay = selectedDelay,
                    activeSeconds = activeSeconds
                )
            }

            // 3. Footer Control Switch Block (Lavender active CTA bar from specifications)
            item {
                FooterControlBar(
                    isRunning = isRunning,
                    currentDelayMinutes = selectedDelay / 60,
                    onToggle = { viewModel.toggleService() }
                )
            }

            // 4. Custom Delay Setting chips
            item {
                MinimalDelaySelector(
                    currentDelay = selectedDelay,
                    onSelectDelay = { viewModel.setDelaySeconds(it) }
                )
            }

            // 5. Permission Alert Card (Only displays when missing crucial permissions)
            if (!hasNotificationPermission || !hasOverlayPermission) {
                item {
                    MinimalPermissionCard(
                        hasNotification = hasNotificationPermission,
                        hasOverlay = hasOverlayPermission,
                        onRequestNotification = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestOverlay = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        },
                        onRefresh = { checkPermissions() }
                    )
                }
            }

            // 6. Stats Grid Block (Extracted from 2-column design template)
            item {
                MinimalStatsGrid(
                    totalWarnings = stats.totalWarnings,
                    avgDuration = viewModel.formatDuration(stats.avgDurationSeconds),
                    totalUnlocks = stats.totalUnlocks
                )
            }

            // 7. History Section Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History Icon",
                        tint = MinimalTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.stats_history_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalTextMain
                    )
                }
            }

            // 8. Historical items
            if (history.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(MinimalTileBg)
                            .padding(24.dp)
                            .testTag("history_empty_placeholder"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.history_empty),
                            color = MinimalTextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                items(history.take(20)) { session ->
                    MinimalHistoryRow(
                        session = session,
                        formatTime = { viewModel.formatTimestamp(it) },
                        formatDuration = { viewModel.formatDuration(it) }
                    )
                }
            }

            // Spacing at bottom
            item {
                Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
            }
        }
    }
}

@Composable
fun MinimalMonitorDial(
    isRunning: Boolean,
    countdown: Int,
    totalDelay: Int,
    activeSeconds: Long
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_card"),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Elegant Outer Circular Boundary
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(230.dp)
                    .border(3.dp, Color(0xFFE0E2EC), CircleShape)
            ) {
                // If running, we display an active gradient progress spinner or smooth trace circle
                if (isRunning) {
                    val progressFloat = if (totalDelay > 0) countdown.toFloat() / totalDelay.toFloat() else 1f
                    CircularProgressIndicator(
                        progress = { progressFloat },
                        modifier = Modifier.size(230.dp),
                        color = MinimalPurplePrimary,
                        strokeWidth = 4.dp,
                        trackColor = Color.Transparent
                    )
                }

                // Inner content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isRunning) {
                        // Format into elegant mm:ss format
                        val minutes = countdown / 60
                        val seconds = countdown % 60
                        val timeString = String.format("%02d:%02d", minutes, seconds)
                        
                        Text(
                            text = timeString,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Light,
                            color = MinimalTextMain,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "直到提醒",
                            fontSize = 12.sp,
                            color = MinimalTextMuted,
                            fontStyle = FontStyle.Italic
                        )
                    } else {
                        Text(
                            text = "--:--",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Light,
                            color = MinimalTextMuted.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "已暂停",
                            fontSize = 12.sp,
                            color = MinimalTextMuted,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Active tracking status pill: "正在监测手机使用状态" (bg: #D1E1FF, text: #001D49)
            val pillBg = if (isRunning) MinimalBlueLight else MinimalTileBg
            val pillText = if (isRunning) MinimalBlueDark else MinimalTextMuted
            val dotColor = if (isRunning) MinimalBlueDark else MinimalTextMuted.copy(alpha = 0.5f)

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(pillBg)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = if (isRunning) "正在监测手机使用状态" else "保护已被暂停",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = pillText
                )
            }
        }
    }
}

@Composable
fun FooterControlBar(
    isRunning: Boolean,
    currentDelayMinutes: Int,
    onToggle: () -> Unit
) {
    // Elegant control bar mimicking the specs footer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MinimalPurpleLight)
            .clickable { onToggle() }
            .padding(24.dp)
            .testTag("toggle_service_btn")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${currentDelayMinutes}分钟强力提醒",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinimalPurpleDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isRunning) "当前状态：已开启" else "当前状态：已关闭",
                    fontSize = 13.sp,
                    color = MinimalTextMuted
                )
            }

            // Custom Elegant minimalist Switch Thumb
            val switchBgColor = if (isRunning) MinimalPurplePrimary else Color(0xFFC4C6D0)
            val thumbAlignment = if (isRunning) Alignment.CenterEnd else Alignment.CenterStart
            
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(switchBgColor)
                    .padding(4.dp),
                contentAlignment = thumbAlignment
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
fun MinimalDelaySelector(
    currentDelay: Int,
    onSelectDelay: (Int) -> Unit
) {
    val delays = listOf(
        10 to "10秒 (测试)",
        60 to "1分钟 (标准)",
        180 to "3分钟",
        300 to "5分钟"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Gear",
                tint = MinimalTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.setting_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MinimalTextMuted,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            delays.forEach { (seconds, label) ->
                val isSelected = currentDelay == seconds
                val bg = if (isSelected) MinimalPurplePrimary else MinimalTileBg
                val tc = if (isSelected) Color.White else MinimalTextMain
                val fw = if (isSelected) FontWeight.Bold else FontWeight.Normal

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable { onSelectDelay(seconds) }
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                        .testTag("delay_chip_$seconds"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = tc,
                        fontSize = 11.sp,
                        fontWeight = fw,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MinimalStatsGrid(
    totalWarnings: Int,
    avgDuration: String,
    totalUnlocks: Int
) {
    // 2-column minimal grid as specified in Clean Minimalism specifications
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Box 1: 今日提醒次数 (Warnings)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MinimalTileBg)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MinimalPurpleLight.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Alerts Icon",
                            tint = MinimalPurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "$totalWarnings",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextMain
                        )
                        Text(
                            text = "今日提醒次数",
                            fontSize = 11.sp,
                            color = MinimalTextMuted
                        )
                    }
                }
            }

            // Box 2: 平均用时 / 节省时间 (Duration)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MinimalTileBg)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MinimalPurpleLight.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Timer Icon",
                            tint = MinimalPurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = avgDuration,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextMain
                        )
                        Text(
                            text = "单次平均时长",
                            fontSize = 11.sp,
                            color = MinimalTextMuted
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Block 3: 解锁次数 (Full width sleek mini tile)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MinimalTileBg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MinimalPurplePrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "今日手机解锁次数",
                    fontSize = 12.sp,
                    color = MinimalTextMuted
                )
            }
            Text(
                text = "$totalUnlocks 次",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MinimalTextMain
            )
        }
    }
}

@Composable
fun MinimalPermissionCard(
    hasNotification: Boolean,
    hasOverlay: Boolean,
    onRequestNotification: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MinimalPurpleLight.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Permissions",
                        tint = MinimalPurplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.permission_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MinimalPurpleDark
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MinimalPurplePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notification permission item
            MinimalPermissionRow(
                title = "通知权限",
                desc = "用以在后台显示状态并发送高优先级提醒。",
                isGranted = hasNotification,
                onGrant = onRequestNotification,
                tag = "notif_perm_btn"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Overlay permission item
            MinimalPermissionRow(
                title = "悬浮窗权限",
                desc = "到达1分钟自动在最前端弹出强制警戒窗口（极为有效）。",
                isGranted = hasOverlay,
                onGrant = onRequestOverlay,
                tag = "overlay_perm_btn"
            )
        }
    }
}

@Composable
fun MinimalPermissionRow(
    title: String,
    desc: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MinimalTextMain
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 10.sp,
                color = MinimalTextMuted,
                lineHeight = 14.sp
            )
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MinimalPurplePrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag(tag)
            ) {
                Text(
                    text = "开启",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MinimalHistoryRow(
    session: UsageSession,
    formatTime: (Long) -> String,
    formatDuration: (Long) -> String
) {
    val durationText = if (session.lockTime > 0) {
        formatDuration(session.durationSeconds)
    } else {
        "监测中..."
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MinimalTileBg)
            .padding(16.dp)
            .testTag("history_item_${session.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "解锁时刻: ${formatTime(session.unlockTime)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MinimalTextMain
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "持续使用: $durationText",
                    fontSize = 12.sp,
                    color = if (session.lockTime > 0) MinimalTextMuted else TomatoRed,
                    fontWeight = if (session.lockTime > 0) FontWeight.Normal else FontWeight.Bold
                )
            }

            if (session.warned) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TomatoRed.copy(alpha = 0.15f))
                        .border(1.dp, TomatoRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "已弹警告",
                        color = TomatoRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
