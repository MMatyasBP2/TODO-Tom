package com.todo.mobile

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH
}

enum class TaskCategory {
    SHOPPING,
    WORK,
    UNIVERSITY,
    PERSONAL
}

enum class TaskRecurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}

data class TaskDto(
    val id: String,
    val title: String,
    val description: String?,
    val priority: TaskPriority,
    val deadlineAt: String?,
    val reminderAt: String?,
    val category: TaskCategory,
    val recurrence: TaskRecurrence,
    val completed: Boolean,
    val completedAt: String?,
    val sourceTaskId: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class CreateTaskRequest(
    val title: String,
    val description: String?,
    val priority: TaskPriority,
    val deadlineAt: String?,
    val reminderAt: String?,
    val category: TaskCategory?,
    val recurrence: TaskRecurrence
)

data class UpdateTaskRequest(
    val title: String,
    val description: String?,
    val priority: TaskPriority,
    val deadlineAt: String?,
    val reminderAt: String?,
    val category: TaskCategory?,
    val recurrence: TaskRecurrence,
    val completed: Boolean
)

interface TaskApi {
    @GET("api/tasks")
    suspend fun getTasks(): List<TaskDto>

    @POST("api/tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): TaskDto

    @PUT("api/tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body request: UpdateTaskRequest): TaskDto

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String)
}

class TaskRepository(private val api: TaskApi) {
    suspend fun getAll(): List<TaskDto> = api.getTasks()

    suspend fun create(request: CreateTaskRequest): TaskDto = api.createTask(request)

    suspend fun update(taskId: String, request: UpdateTaskRequest): TaskDto = api.updateTask(taskId, request)

    suspend fun delete(id: String) {
        api.deleteTask(id)
    }
}

object ReminderScheduler {
    fun sync(context: Context, tasks: List<TaskDto>) {
        tasks.forEach { task ->
            schedule(context, task)
        }
    }

    fun schedule(context: Context, task: TaskDto) {
        val manager = WorkManager.getInstance(context)
        val uniqueName = uniqueName(task.id)

        val reminderEpochMillis = parseEpoch(task.reminderAt)
        val nowMillis = System.currentTimeMillis()

        if (task.completed || reminderEpochMillis == null || reminderEpochMillis <= nowMillis) {
            manager.cancelUniqueWork(uniqueName)
            return
        }

        val delay = reminderEpochMillis - nowMillis
        val input = Data.Builder()
            .putString(ReminderWorker.KEY_TASK_ID, task.id)
            .putString(ReminderWorker.KEY_TASK_TITLE, task.title)
            .putString(ReminderWorker.KEY_TASK_DESCRIPTION, task.description ?: "Task deadline")
            .build()

        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .build()

        manager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, work)
    }

    fun cancel(context: Context, taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(taskId))
    }

    private fun parseEpoch(value: String?): Long? {
        return runCatching { value?.let { Instant.parse(it).toEpochMilli() } }.getOrNull()
    }

    private fun uniqueName(taskId: String): String = "task_reminder_$taskId"
}

data class TaskUiState(
    val loading: Boolean = false,
    val tasks: List<TaskDto> = emptyList(),
    val error: String? = null
)

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {
    var uiState by mutableStateOf(TaskUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.getAll() }
            }.onSuccess { tasks ->
                uiState = uiState.copy(loading = false, tasks = tasks.sortedWith(compareBy({ it.completed }, { it.priority })))
            }.onFailure { throwable ->
                uiState = uiState.copy(loading = false, error = throwable.message ?: "Something went wrong")
            }
        }
    }

    fun create(request: CreateTaskRequest, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.create(request) }
            }.onSuccess {
                onDone()
                refresh()
            }.onFailure { throwable ->
                uiState = uiState.copy(error = throwable.message ?: "Create failed")
            }
        }
    }

    fun toggleStatus(task: TaskDto) {
        val request = UpdateTaskRequest(
            title = task.title,
            description = task.description,
            priority = task.priority,
            deadlineAt = task.deadlineAt,
            reminderAt = task.reminderAt,
            category = task.category,
            recurrence = task.recurrence,
            completed = !task.completed
        )

        update(task.id, request)
    }

    fun update(taskId: String, request: UpdateTaskRequest) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.update(taskId, request) }
            }.onSuccess {
                refresh()
            }.onFailure { throwable ->
                uiState = uiState.copy(error = throwable.message ?: "Update failed")
            }
        }
    }

    fun delete(taskId: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.delete(taskId) }
            }.onSuccess {
                refresh()
            }.onFailure { throwable ->
                uiState = uiState.copy(error = throwable.message ?: "Delete failed")
            }
        }
    }
}

class TaskViewModelFactory(private val repository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val api: TaskApi by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        val gson = GsonBuilder().create()

        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(TaskApi::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                val viewModel: TaskViewModel = viewModel(
                    factory = TaskViewModelFactory(TaskRepository(api))
                )
                TaskScreen(viewModel = viewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel) {
    val uiState = viewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var newTitle by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var newPriority by remember { mutableStateOf(TaskPriority.MEDIUM) }
    var newDeadlineAt by remember { mutableStateOf<Instant?>(null) }
    var newReminderAt by remember { mutableStateOf<Instant?>(null) }
    var newCategory by remember { mutableStateOf<TaskCategory?>(null) }
    var newRecurrence by remember { mutableStateOf(TaskRecurrence.NONE) }
    var isCreateExpanded by remember { mutableStateOf(false) }

    var editTask by remember { mutableStateOf<TaskDto?>(null) }
    var deleteTask by remember { mutableStateOf<TaskDto?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    LaunchedEffect(uiState.tasks) {
        ReminderScheduler.sync(context, uiState.tasks)
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("TODO Tom") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFF6EA))) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("New task", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { isCreateExpanded = !isCreateExpanded }) {
                            Text(if (isCreateExpanded) "Collapse" else "Expand")
                        }
                    }

                    if (isCreateExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Title") }
                            )

                            OutlinedTextField(
                                value = newDescription,
                                onValueChange = { newDescription = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Description") }
                            )

                            EnumChips(
                                title = "Priority",
                                options = TaskPriority.values().toList(),
                                selected = newPriority,
                                onSelected = { newPriority = it },
                                label = { it.name }
                            )

                            NullableCategoryChips(newCategory) { newCategory = it }

                            EnumChips(
                                title = "Recurrence",
                                options = TaskRecurrence.values().toList(),
                                selected = newRecurrence,
                                onSelected = { newRecurrence = it },
                                label = { it.name }
                            )

                            DateTimePickerField(
                                label = "Due date",
                                value = newDeadlineAt,
                                onValue = { newDeadlineAt = it }
                            )

                            DateTimePickerField(
                                label = "Reminder",
                                value = newReminderAt,
                                onValue = { newReminderAt = it }
                            )

                            Button(
                                onClick = {
                                    val title = newTitle.trim()
                                    if (title.isNotEmpty()) {
                                        viewModel.create(
                                            CreateTaskRequest(
                                                title = title,
                                                description = newDescription.trim().ifBlank { null },
                                                priority = newPriority,
                                                deadlineAt = newDeadlineAt?.toString(),
                                                reminderAt = newReminderAt?.toString(),
                                                category = newCategory,
                                                recurrence = newRecurrence
                                            )
                                        ) {
                                            newTitle = ""
                                            newDescription = ""
                                            newPriority = TaskPriority.MEDIUM
                                            newDeadlineAt = null
                                            newReminderAt = null
                                            newCategory = null
                                            newRecurrence = TaskRecurrence.NONE
                                            isCreateExpanded = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Create task")
                            }
                        }
                    } else {
                        Text("The panel is collapsed by default so the task list stays visible.")
                    }
                }
            }

            if (uiState.loading) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }

            TaskLane(
                title = "To Do",
                tasks = uiState.tasks.filter { !it.completed },
                onToggleStatus = { viewModel.toggleStatus(it) },
                onEdit = { editTask = it },
                onDelete = { deleteTask = it }
            )

            Divider()

            TaskLane(
                title = "Done",
                tasks = uiState.tasks.filter { it.completed },
                onToggleStatus = { viewModel.toggleStatus(it) },
                onEdit = { editTask = it },
                onDelete = { deleteTask = it }
            )
        }
    }

    editTask?.let { task ->
        EditTaskDialog(
            task = task,
            onDismiss = { editTask = null },
            onSave = { request ->
                viewModel.update(task.id, request)
                editTask = null
            }
        )
    }

    deleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTask = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(task.id)
                        ReminderScheduler.cancel(context, task.id)
                        deleteTask = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTask = null }) { Text("Cancel") }
            },
            title = { Text("Delete task") },
            text = { Text("Are you sure you want to delete this task?") }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskLane(
    title: String,
    tasks: List<TaskDto>,
    onToggleStatus: (TaskDto) -> Unit,
    onEdit: (TaskDto) -> Unit,
    onDelete: (TaskDto) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(tasks, key = { it.id }) { task ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDelete(task) },
                colors = CardDefaults.cardColors(
                    containerColor = if (task.completed) Color(0xFFE8F5E9) else Color(0xFFFFFFFF)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!task.description.isNullOrBlank()) {
                        Text(task.description, style = MaterialTheme.typography.bodyMedium)
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text("Priority: ${task.priority.name}") })
                        AssistChip(onClick = {}, label = { Text("Category: ${task.category.name}") })
                        AssistChip(onClick = {}, label = { Text("Repeat: ${task.recurrence.name}") })
                    }

                    task.deadlineAt?.let {
                        Text("Due date: ${formatInstant(it)}")
                    }
                    task.reminderAt?.let {
                        Text("Reminder: ${formatInstant(it)}")
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { onToggleStatus(task) },
                            label = { Text(if (task.completed) "Move back to TODO" else "Mark done") }
                        )
                        AssistChip(onClick = { onEdit(task) }, label = { Text("Edit") })
                        AssistChip(onClick = { onDelete(task) }, label = { Text("Delete") })
                    }
                }
            }
        }
    }
}

@Composable
private fun EditTaskDialog(
    task: TaskDto,
    onDismiss: () -> Unit,
    onSave: (UpdateTaskRequest) -> Unit
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var description by remember(task.id) { mutableStateOf(task.description.orEmpty()) }
    var completed by remember(task.id) { mutableStateOf(task.completed) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var deadlineAt by remember(task.id) { mutableStateOf(parseInstant(task.deadlineAt)) }
    var reminderAt by remember(task.id) { mutableStateOf(parseInstant(task.reminderAt)) }
    var category by remember(task.id) { mutableStateOf<TaskCategory?>(task.category) }
    var recurrence by remember(task.id) { mutableStateOf(task.recurrence) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        UpdateTaskRequest(
                            title = title.trim(),
                            description = description.trim().ifBlank { null },
                            priority = priority,
                            deadlineAt = deadlineAt?.toString(),
                            reminderAt = reminderAt?.toString(),
                            category = category,
                            recurrence = recurrence,
                            completed = completed
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") }
                )

                EnumChips(
                    title = "Priority",
                    options = TaskPriority.values().toList(),
                    selected = priority,
                    onSelected = { priority = it },
                    label = { it.name }
                )

                NullableCategoryChips(category) { category = it }

                EnumChips(
                    title = "Recurrence",
                    options = TaskRecurrence.values().toList(),
                    selected = recurrence,
                    onSelected = { recurrence = it },
                    label = { it.name }
                )

                DateTimePickerField("Due date", deadlineAt) { deadlineAt = it }
                DateTimePickerField("Reminder", reminderAt) { reminderAt = it }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Completed")
                    Switch(checked = completed, onCheckedChange = { completed = it })
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> EnumChips(
    title: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String
) {
    Text(title, fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NullableCategoryChips(selected: TaskCategory?, onSelected: (TaskCategory?) -> Unit) {
    Text("Category", fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text("AUTO") }
        )
        TaskCategory.values().forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(option.name) }
            )
        }
    }
}

@Composable
private fun DateTimePickerField(label: String, value: Instant?, onValue: (Instant?) -> Unit) {
    val context = LocalContext.current
    val localValue = value?.atZone(ZoneId.systemDefault())?.toLocalDateTime()

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$label: ${localValue?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) ?: "none"}")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { pickDateTime(context, localValue ?: LocalDateTime.now()) { onValue(it) } }) {
                Text("Pick")
            }
            TextButton(onClick = { onValue(null) }) {
                Text("Clear")
            }
        }
    }
}

private fun pickDateTime(context: Context, initial: LocalDateTime, onPicked: (Instant) -> Unit) {
    val datePicker = DatePickerDialog(
        context,
        { _, year, month, day ->
            val timePicker = TimePickerDialog(
                context,
                { _, hour, minute ->
                    val localDateTime = LocalDateTime.of(year, month + 1, day, hour, minute)
                    onPicked(localDateTime.atZone(ZoneId.systemDefault()).toInstant())
                },
                initial.hour,
                initial.minute,
                true
            )
            timePicker.show()
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth
    )
    datePicker.show()
}

private fun parseInstant(value: String?): Instant? = runCatching { value?.let { Instant.parse(it) } }.getOrNull()

private fun formatInstant(value: String): String = runCatching {
    val instant = Instant.parse(value)
    instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrElse { value }
