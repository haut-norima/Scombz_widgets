package com.testapp.scombz_widgets

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.testapp.scombz_widgets.data.model.Assignment
import com.testapp.scombz_widgets.data.model.AssignmentSource
import com.testapp.scombz_widgets.data.model.Course
import com.testapp.scombz_widgets.data.model.PeriodTime
import com.testapp.scombz_widgets.data.model.TimetableSheet
import com.testapp.scombz_widgets.data.network.SessionExpiredException
import com.testapp.scombz_widgets.data.repository.ExtraAssignmentRepository
import com.testapp.scombz_widgets.data.repository.ScombzRepository
import com.testapp.scombz_widgets.ui.ExtraAssignmentTab
import com.testapp.scombz_widgets.ui.theme.ScombzwidgetsTheme
import com.testapp.scombz_widgets.widget.AssignmentWidget
import com.testapp.scombz_widgets.widget.TimetableWidget
import com.testapp.scombz_widgets.worker.DataSyncWorker
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {

    private lateinit var repository: ScombzRepository

    // ログイン・同期完了後にComposableへ通知するトリガー
    private var syncTrigger by mutableStateOf(0)

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val cookies = result.data?.getStringExtra(LoginActivity.RESULT_COOKIES)
            if (cookies != null) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    repository.saveCookies(cookies)
                    Toast.makeText(this@MainActivity, "ログイン成功。データを取得中...", Toast.LENGTH_SHORT).show()
                    // 初回同期
                    syncData()
                    // 同期完了をUIに通知（LaunchedEffectが再実行される）
                    syncTrigger++
                    // 定期同期をスケジュール
                    DataSyncWorker.schedule(this@MainActivity)
                }
            }
        }
    }

    private suspend fun syncData() {
        repository.syncTimetable()
        val assignResult = repository.syncAssignments()
        assignResult.fold(
            onSuccess = { list ->
                val msg = if (list.isEmpty()) "未提出の課題はありません" else "課題 ${list.size} 件を取得しました"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            },
            onFailure = { e ->
                if (e is SessionExpiredException) {
                    Toast.makeText(this, "セッションが切れました。再ログインしてください", Toast.LENGTH_LONG).show()
                    syncTrigger++ // UIをログアウト状態に更新
                } else {
                    Toast.makeText(this, "課題取得失敗: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
        TimetableWidget.updateWidget(this)
        AssignmentWidget.updateWidget(this)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = ScombzRepository(this)

        // バス同期はログイン不要のため、起動時に常にスケジュール
        DataSyncWorker.schedule(this)

        // バッテリー最適化の除外をリクエスト（毎分アラームが OEM の電力管理で遮断されるのを防ぐ）
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }

        // Android 12+: 正確なアラームの権限がなければ設定画面へ誘導する
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(
                    this,
                    "ウィジェットの毎分更新のため「アラームとリマインダー」を許可してください",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
        }

        setContent {
            ScombzwidgetsTheme {
                MainScreen(
                    repository = repository,
                    syncTrigger = syncTrigger,
                    onLoginClick = {
                        loginLauncher.launch(LoginActivity.createIntent(this))
                    },
                    onLogoutClick = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            repository.clearCookies()
                            // DataSyncWorkerはバス同期のため継続（ScombZ同期はCookie不要チェックでスキップされる）
                            Toast.makeText(this@MainActivity, "ログアウトしました", Toast.LENGTH_SHORT).show()
                            syncTrigger++
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    repository: ScombzRepository,
    syncTrigger: Int,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var timetable by remember { mutableStateOf<TimetableSheet?>(null) }
    var assignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var lastSync by remember { mutableStateOf<String?>(null) }
    var debugInfo by remember { mutableStateOf<String?>(null) }

    // syncTrigger が変わるたびに再読み込み（ログイン後・同期後も反映される）
    LaunchedEffect(syncTrigger) {
        repository.loadCookies()
        isLoggedIn = repository.isLoggedIn()
        timetable = repository.loadTimetableLocal()
        // ScombZ課題 + 現在アクティブなエクストラ課題をマージ表示
        assignments = repository.loadMergedAssignments()
        lastSync = repository.getLastSyncTime()
        debugInfo = repository.getDebugInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ScombZ Widgets")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (isLoggedIn) {
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    repository.syncTimetable().onSuccess { timetable = it }
                                    val assignResult = repository.syncAssignments()
                                    assignResult.onSuccess { list ->
                                        assignments = list
                                    }
                                    // 最新データを再読み込み（パース成功分 + エクストラ課題を反映）
                                    assignments = repository.loadMergedAssignments()
                                    lastSync = repository.getLastSyncTime()
                                    debugInfo = repository.getDebugInfo()
                                    // 結果をトーストで表示
                                    assignResult.fold(
                                        onSuccess = { list ->
                                            val msg = if (list.isEmpty()) "未提出の課題はありません"
                                                      else "課題 ${list.size} 件を取得しました"
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        },
                                        onFailure = { e ->
                                            if (e is SessionExpiredException) {
                                                isLoggedIn = false
                                                Toast.makeText(context, "セッションが切れました。再ログインしてください", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "課題取得失敗: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    )
                                } finally {
                                    isLoading = false
                                }
                            }
                        }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "同期",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ログイン状態バー
            LoginStatusBar(
                isLoggedIn = isLoggedIn,
                lastSync = lastSync,
                onLoginClick = onLoginClick,
                onLogoutClick = {
                    onLogoutClick()
                    isLoggedIn = false
                    timetable = null
                    assignments = emptyList()
                }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // タブ
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("時間割") },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("課題") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("エクストラ") },
                    icon = { Icon(Icons.Default.EventRepeat, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> TimetableTab(timetable, isLoggedIn, onLoginClick, repository, scope) {
                    isLoading = it
                }
                1 -> AssignmentTab(assignments, isLoggedIn, onLoginClick, debugInfo)
                2 -> {
                    val extraRepo = remember { ExtraAssignmentRepository(context) }
                    ExtraAssignmentTab(
                        repository = extraRepo,
                        timetable = timetable,
                        onChanged = {
                            // 変更時は課題リストと全ウィジェットを即時更新
                            assignments = repository.loadMergedAssignments()
                            scope.launch {
                                AssignmentWidget.updateWidget(context)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginStatusBar(
    isLoggedIn: Boolean,
    lastSync: String?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoggedIn)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ステータスインジケーター
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLoggedIn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isLoggedIn) "ログイン中" else "未ログイン",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (lastSync != null) {
                    Text(
                        text = "最終同期: ${formatSyncTime(lastSync)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLoggedIn) {
                FilledTonalButton(onClick = onLogoutClick) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ログアウト", fontSize = 12.sp)
                }
            } else {
                Button(onClick = onLoginClick) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ログイン", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TimetableTab(
    timetable: TimetableSheet?,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    repository: ScombzRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    setLoading: (Boolean) -> Unit
) {
    if (timetable == null) {
        EmptyStateView(
            icon = Icons.Default.Schedule,
            title = "時間割データがありません",
            description = if (!isLoggedIn) "ScombZにログインしてデータを取得してください"
            else "同期ボタンを押してデータを取得してください",
            actionLabel = if (!isLoggedIn) "ログイン" else "データを同期",
            onAction = {
                if (!isLoggedIn) onLoginClick()
                else scope.launch {
                    setLoading(true)
                    repository.syncTimetable()
                    setLoading(false)
                }
            }
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "${timetable.year}年度 ${timetable.term}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val days = listOf("月", "火", "水", "木", "金", "土")
        val periods = PeriodTime.entries

        // 曜日ごとに表示
        days.forEach { day ->
            val dayCourses = periods.mapNotNull { period ->
                val courses = timetable.grid[period.label]?.get(day)
                if (!courses.isNullOrEmpty()) period to courses else null
            }

            if (dayCourses.isNotEmpty()) {
                item {
                    Text(
                        text = "${day}曜日",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(dayCourses) { (period, courses) ->
                    courses.forEach { course ->
                        CourseCard(period = period, course = course)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(period: PeriodTime, course: Course) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 時限ラベル
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period.label.take(1),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.baseName + (course.qPeriod?.let { " ($it)" } ?: ""),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (course.classroom.isNotBlank()) {
                    Text(
                        text = course.classroom,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (course.teachers.isNotEmpty()) {
                    Text(
                        text = course.teachers.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignmentTab(
    assignments: List<Assignment>,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    debugInfo: String? = null
) {
    val now = LocalDateTime.now()

    if (assignments.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            EmptyStateView(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = "課題データがありません",
                description = if (!isLoggedIn) "ScombZにログインしてデータを取得してください"
                else "未提出の課題はありません（期限内）",
                actionLabel = if (!isLoggedIn) "ログイン" else null,
                onAction = if (!isLoggedIn) onLoginClick else null
            )
            if (debugInfo != null) {
                DebugInfoCard(debugInfo)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(assignments) { assignment ->
            AssignmentCard(assignment = assignment, now = now)
        }
        if (debugInfo != null) {
            item {
                DebugInfoCard(debugInfo)
            }
        }
    }
}

@Composable
private fun DebugInfoCard(debugInfo: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "🔍 最終同期デバッグ情報",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = debugInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AssignmentCard(assignment: Assignment, now: LocalDateTime) {
    val urgency = assignment.urgencyLevel(now)
    val containerColor = when (urgency) {
        2 -> MaterialTheme.colorScheme.errorContainer
        1 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (assignment.source == AssignmentSource.EXTRA) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF6A1B9A))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "外部",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = assignment.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (assignment.courseName.isNotBlank()) {
                    Text(
                        text = assignment.courseName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "残り",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = assignment.remainingTimeText(now),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (urgency) {
                        2 -> MaterialTheme.colorScheme.error
                        1 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

private fun formatSyncTime(isoString: String): String {
    return try {
        val dt = LocalDateTime.parse(isoString)
        "${dt.monthValue}/${dt.dayOfMonth} ${dt.hour}:${dt.minute.toString().padStart(2, '0')}"
    } catch (_: Exception) {
        isoString
    }
}
