package com.chargetone.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargetone.app.AppGraph
import com.chargetone.app.data.EventLog
import com.chargetone.app.data.Settings
import com.chargetone.app.service.ChargingService
import com.chargetone.app.service.MonitorStatus
import kotlinx.coroutines.launch

/** 三个可独立配置的提示音槽位 */
private enum class ToneSlot(val title: String) {
    CHARGE("充电时"),
    UNPLUG("断开充电时"),
    FULL("充满电时"),
}

private fun Settings.toneFor(slot: ToneSlot): String? = when (slot) {
    ToneSlot.CHARGE -> chargeTone
    ToneSlot.UNPLUG -> unplugTone
    ToneSlot.FULL -> fullTone
}

private fun Settings.enabledFor(slot: ToneSlot): Boolean = when (slot) {
    ToneSlot.CHARGE -> chargeEnabled
    ToneSlot.UNPLUG -> unplugEnabled
    ToneSlot.FULL -> fullEnabled
}

private fun Settings.withTone(slot: ToneSlot, token: String?): Settings = when (slot) {
    ToneSlot.CHARGE -> copy(chargeTone = token)
    ToneSlot.UNPLUG -> copy(unplugTone = token)
    ToneSlot.FULL -> copy(fullTone = token)
}

private fun Settings.withEnabled(slot: ToneSlot, value: Boolean): Settings = when (slot) {
    ToneSlot.CHARGE -> copy(chargeEnabled = value)
    ToneSlot.UNPLUG -> copy(unplugEnabled = value)
    ToneSlot.FULL -> copy(fullEnabled = value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val events by EventLog.events.collectAsStateWithLifecycle()
    val serviceRunning by MonitorStatus.running.collectAsStateWithLifecycle()

    var pendingSlot by remember { mutableStateOf<ToneSlot?>(null) }
    var guideVisible by remember { mutableStateOf(false) }

    fun startMonitor() {
        runCatching { ChargingService.start(context) }
            .onFailure { error ->
                scope.launch {
                    snackbarHostState.showSnackbar("启动后台监听失败：${error.javaClass.simpleName}")
                }
            }
        scope.launch { AppGraph.settings.update { it.copy(monitorEnabled = true) } }
    }

    fun stopMonitor() {
        ChargingService.stop(context)
        scope.launch { AppGraph.settings.update { it.copy(monitorEnabled = false) } }
    }

    // 通知权限只影响通知能否显示；即使用户拒绝，前台服务依然可以运行
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startMonitor() }

    fun toggleMonitor(enabled: Boolean) {
        if (!enabled) {
            stopMonitor()
            return
        }
        val needPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMonitor()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri == null || slot == null) return@rememberLauncherForActivityResult

        val previousToken = settings.toneFor(slot)

        scope.launch {
            val token = AppGraph.toneStore.import(uri)
            if (token == null) {
                snackbarHostState.showSnackbar("无法读取该音频文件，请换一个试试")
                return@launch
            }
            AppGraph.settings.update { it.withTone(slot, token) }
            AppGraph.toneStore.delete(previousToken)
            snackbarHostState.showSnackbar("已设为${slot.title}提示音")
        }
    }

    fun preview(token: String?) {
        val file = AppGraph.toneStore.resolve(token)
        if (file == null) {
            scope.launch { snackbarHostState.showSnackbar("请先选择音频文件") }
            return
        }
        AppGraph.player.play(file, settings.toneVolumePercent)
    }

    fun pick(slot: ToneSlot) {
        pendingSlot = slot
        picker.launch(arrayOf("audio/*"))
    }

    fun clear(slot: ToneSlot) {
        val token = settings.toneFor(slot)
        scope.launch {
            AppGraph.settings.update { it.withTone(slot, null) }
            AppGraph.toneStore.delete(token)
            snackbarHostState.showSnackbar("已清除${slot.title}提示音")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("充电精灵") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ServiceSection(
                enabled = settings.monitorEnabled,
                running = serviceRunning,
                onToggle = { toggleMonitor(it) },
                onShowGuide = { guideVisible = true },
            )

            ToneSlot.entries.forEach { slot ->
                ToneSection(
                    slot = slot,
                    enabled = settings.enabledFor(slot),
                    toneName = AppGraph.toneStore.displayName(settings.toneFor(slot)),
                    onToggle = { value ->
                        scope.launch { AppGraph.settings.update { it.withEnabled(slot, value) } }
                    },
                    onPick = { pick(slot) },
                    onPreview = { preview(settings.toneFor(slot)) },
                    onClear = { clear(slot) },
                )
            }

            VolumeSection(
                percent = settings.toneVolumePercent,
                onCommit = { value ->
                    scope.launch { AppGraph.settings.update { it.copy(toneVolumePercent = value) } }
                },
            )

            EventsSection(events = events, onClear = { EventLog.clear() })

            GuideSection(onShowGuide = { guideVisible = true })
        }
    }

    if (guideVisible) {
        GuideDialog(
            onDismiss = { guideVisible = false },
            onOpenAppSettings = {
                guideVisible = false
                openAppSettings(context)
            },
        )
    }
}

@Composable
private fun ServiceSection(
    enabled: Boolean,
    running: Boolean,
    onToggle: (Boolean) -> Unit,
    onShowGuide: () -> Unit,
) {
    val status = when {
        running -> "运行中"
        enabled -> "已开启但未运行：打开应用会自动恢复"
        else -> "已关闭"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("后台监听服务", style = MaterialTheme.typography.titleMedium)
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Text(
                text = "插拔充电器的广播只能在应用进程存活时收到，因此需要常驻一条「正在监听」的通知。" +
                    "关闭开关后通知消失，插拔也不会再响。",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onShowGuide) { Text("查看保活设置指引") }
        }
    }
}

@Composable
private fun ToneSection(
    slot: ToneSlot,
    enabled: Boolean,
    toneName: String?,
    onToggle: (Boolean) -> Unit,
    onPick: () -> Unit,
    onPreview: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(slot.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = toneName ?: "未选择音频",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPick, modifier = Modifier.weight(1f)) { Text("选择音频") }
                OutlinedButton(
                    onClick = onPreview,
                    enabled = toneName != null,
                    modifier = Modifier.weight(1f),
                ) { Text("试听") }
                OutlinedButton(onClick = onClear, enabled = toneName != null) { Text("清除") }
            }

            if (slot == ToneSlot.FULL) {
                Text(
                    text = "每次充电到 100% 时响一次；拔掉充电器后重新开始计算。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun VolumeSection(percent: Int, onCommit: (Int) -> Unit) {
    var sliderValue by remember(percent) { mutableFloatStateOf(percent.toFloat()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("提示音音量", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text("${sliderValue.toInt()}%", style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onCommit(sliderValue.toInt()) },
                valueRange = 0f..100f,
            )
            Text(
                text = "提示音走系统「通知音量」通道，静音或勿扰模式下不会发声；此处是在通知音量基础上的额外衰减。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EventsSection(events: List<String>, onClear: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "最近事件",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("清空") }
            }
            if (events.isEmpty()) {
                Text(
                    text = "插拔一次充电器，这里会显示检测与播放结果。",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                events.take(12).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideSection(onShowGuide: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("使用前必读", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "系统自带的充电提示音无法被第三方应用替换，建议先到「设置 → 声音与振动」关闭它，" +
                    "再让本应用播放你选择的音频。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onShowGuide) { Text("查看完整说明") }
        }
    }
}

@Composable
private fun GuideDialog(onDismiss: () -> Unit, onOpenAppSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用说明与限制") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "1. 本应用只负责「在插拔充电器、充满电时播放你选定的音频」，不会修改 ColorOS 自带的充电音效。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "2. 插拔充电器属于受限制的隐式广播，必须由常驻的前台服务接收，因此通知栏会有一条「正在监听」通知，" +
                        "这是系统要求，无法隐藏。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "3. 提示音使用系统通知音量，静音模式、勿扰模式下不会响，这是 Android 的音频策略。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "4. 充满电提示音依赖系统上报的电量状态：若开启了「充电保护 / 限充 80%」，电量不会到 100%，" +
                        "该提示音也不会响。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "5. 为保证服务不被清理，请在「设置 → 应用 → 充电精灵 → 电池」中选择「不限制」，" +
                        "并在手机管家中允许自启动与后台运行。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "6. 重启后应用会尝试自动恢复服务；若系统拦截，打开一次应用即可恢复。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpenAppSettings) { Text("打开应用设置") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
