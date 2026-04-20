package com.testapp.scombz_widgets.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.testapp.scombz_widgets.data.model.ExtraAssignmentSchedule
import com.testapp.scombz_widgets.data.model.TimetableSheet
import com.testapp.scombz_widgets.data.repository.ExtraAssignmentRepository

private val DAY_LABELS = listOf("月", "火", "水", "木", "金", "土", "日")

private fun dayLabel(dow: Int): String =
    DAY_LABELS.getOrNull(dow - 1) ?: "?"

/**
 * エクストラ課題タブのエントリポイント。
 */
@Composable
fun ExtraAssignmentTab(
    repository: ExtraAssignmentRepository,
    timetable: TimetableSheet?,
    onChanged: () -> Unit
) {
    var schedules by remember { mutableStateOf(repository.loadAll()) }
    var showDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ExtraAssignmentSchedule?>(null) }
    var deleteTarget by remember { mutableStateOf<ExtraAssignmentSchedule?>(null) }

    val courseOptions = remember(timetable) {
        extractCourseNames(timetable)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (schedules.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EventRepeat,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "エクストラ課題",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ScombZの外で提出する課題を手動で登録し、\n毎週の周期で自動表示できます。\n\n右下の「+」ボタンから追加してください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 16.dp, bottom = 96.dp // FABに隠れないように余白
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "登録済みスケジュール (${schedules.size}件)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "毎週、指定した曜日と時刻になると課題として自動表示されます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onToggle = { enabled ->
                            repository.setEnabled(schedule.id, enabled)
                            schedules = repository.loadAll()
                            onChanged()
                        },
                        onEdit = {
                            editTarget = schedule
                            showDialog = true
                        },
                        onDelete = {
                            deleteTarget = schedule
                        }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editTarget = null
                showDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "エクストラ課題を追加")
        }
    }

    if (showDialog) {
        ExtraAssignmentEditDialog(
            initial = editTarget,
            courseOptions = courseOptions,
            onDismiss = { showDialog = false },
            onSave = { schedule ->
                repository.upsert(schedule)
                schedules = repository.loadAll()
                showDialog = false
                onChanged()
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("削除しますか？") },
            text = {
                Text("「${target.title}」を削除します。この操作は取り消せません。")
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.delete(target.id)
                    schedules = repository.loadAll()
                    deleteTarget = null
                    onChanged()
                }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: ExtraAssignmentSchedule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val alpha = if (schedule.enabled) 1f else 0.5f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schedule.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                    if (schedule.courseName.isNotBlank()) {
                        Text(
                            text = schedule.courseName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 周期情報
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha * 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.EventRepeat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "毎週 ${dayLabel(schedule.startDayOfWeek)}曜 ${formatTime(schedule.startHour, schedule.startMinute)} → " +
                           "${dayLabel(schedule.endDayOfWeek())}曜 ${schedule.endTime().let { formatTime(it.hour, it.minute) }}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            if (!schedule.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = schedule.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("編集")
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * エクストラ課題の追加 / 編集ダイアログ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraAssignmentEditDialog(
    initial: ExtraAssignmentSchedule?,
    courseOptions: List<String>,
    onDismiss: () -> Unit,
    onSave: (ExtraAssignmentSchedule) -> Unit
) {
    val isEdit = initial != null

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var courseName by remember { mutableStateOf(initial?.courseName ?: "") }
    var startDow by remember { mutableIntStateOf(initial?.startDayOfWeek ?: 1) }
    var startHour by remember { mutableIntStateOf(initial?.startHour ?: 9) }
    var startMinute by remember { mutableIntStateOf(initial?.startMinute ?: 0) }

    val initialEndDow = initial?.endDayOfWeek() ?: 2
    val initialEndTime = initial?.endTime()
    var endDow by remember { mutableIntStateOf(initialEndDow) }
    var endHour by remember { mutableIntStateOf(initialEndTime?.hour ?: 23) }
    var endMinute by remember { mutableIntStateOf(initialEndTime?.minute ?: 0) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        title = {
            Text(if (isEdit) "エクストラ課題を編集" else "エクストラ課題を追加")
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 520.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorMessage = null },
                    label = { Text("課題タイトル *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                CourseNameField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    options = courseOptions
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "表示開始",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                DayOfWeekSelector(
                    selected = startDow,
                    onSelect = { startDow = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TimeSelectorRow(
                    label = "時刻",
                    hour = startHour,
                    minute = startMinute,
                    onClick = { showStartTimePicker = true }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "期限 (終了時刻)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                DayOfWeekSelector(
                    selected = endDow,
                    onSelect = { endDow = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TimeSelectorRow(
                    label = "時刻",
                    hour = endHour,
                    minute = endMinute,
                    onClick = { showEndTimePicker = true }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 期間プレビュー
                val previewDuration = ExtraAssignmentSchedule.computeDuration(
                    startDow, startHour, startMinute,
                    endDow, endHour, endMinute
                )
                Text(
                    text = "期間: ${formatDuration(previewDuration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("メモ (任意)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isBlank()) {
                    errorMessage = "課題タイトルを入力してください"
                    return@Button
                }
                val duration = ExtraAssignmentSchedule.computeDuration(
                    startDow, startHour, startMinute,
                    endDow, endHour, endMinute
                )
                if (duration <= 0) {
                    errorMessage = "期間が無効です"
                    return@Button
                }
                val schedule = (initial ?: ExtraAssignmentSchedule(
                    title = title.trim(),
                    courseName = courseName.trim(),
                    startDayOfWeek = startDow,
                    startHour = startHour,
                    startMinute = startMinute,
                    durationMinutes = duration
                )).copy(
                    title = title.trim(),
                    courseName = courseName.trim(),
                    startDayOfWeek = startDow,
                    startHour = startHour,
                    startMinute = startMinute,
                    durationMinutes = duration,
                    note = note.trim().ifBlank { null }
                )
                onSave(schedule)
            }) {
                Text(if (isEdit) "更新" else "追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )

    if (showStartTimePicker) {
        TimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onConfirm = { h, m ->
                startHour = h; startMinute = m; showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }
    if (showEndTimePicker) {
        TimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onConfirm = { h, m ->
                endHour = h; endMinute = m; showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

@Composable
private fun DayOfWeekSelector(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (dow in 1..7) {
            FilterChip(
                selected = selected == dow,
                onClick = { onSelect(dow) },
                label = {
                    Text(
                        dayLabel(dow),
                        fontSize = 12.sp,
                        fontWeight = if (selected == dow) FontWeight.Bold else FontWeight.Normal
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimeSelectorRow(
    label: String,
    hour: Int,
    minute: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClick) {
            Text(
                text = formatTime(hour, minute),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("時刻を選択") },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseNameField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, options) {
        if (value.isBlank()) options
        else options.filter { it.contains(value, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("科目名 (任意)") },
            singleLine = true,
            trailingIcon = {
                if (options.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
        )
        if (options.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded && filtered.isNotEmpty(),
                onDismissRequest = { expanded = false }
            ) {
                filtered.take(8).forEach { course ->
                    DropdownMenuItem(
                        text = { Text(course) },
                        onClick = {
                            onValueChange(course)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// ----- Utility -----

private fun formatTime(hour: Int, minute: Int): String {
    val hh = hour.toString().padStart(2, '0')
    val mm = minute.toString().padStart(2, '0')
    return "$hh:$mm"
}

private fun formatDuration(minutes: Long): String {
    val days = minutes / (24 * 60)
    val rem = minutes % (24 * 60)
    val hours = rem / 60
    val mins = rem % 60
    val parts = buildList {
        if (days > 0) add("${days}日")
        if (hours > 0) add("${hours}時間")
        if (mins > 0) add("${mins}分")
    }
    return if (parts.isEmpty()) "0分" else parts.joinToString("")
}

/**
 * 時間割シートから授業名の一覧（重複除去）を抽出する。
 */
private fun extractCourseNames(sheet: TimetableSheet?): List<String> {
    if (sheet == null) return emptyList()
    val names = linkedSetOf<String>()
    sheet.grid.values.forEach { dayMap ->
        dayMap.values.forEach { courses ->
            courses.forEach { course ->
                if (course.baseName.isNotBlank()) {
                    names.add(course.baseName)
                }
            }
        }
    }
    return names.toList()
}
