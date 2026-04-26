package com.todo.taskmanager.task

import java.time.Instant
import javax.validation.constraints.NotBlank

data class CreateTaskRequest(
    @field:NotBlank(message = "Title cannot be empty.")
    val title: String,
    val description: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val deadlineAt: Instant? = null,
    val reminderAt: Instant? = null,
    val category: TaskCategory? = null,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE
)

data class UpdateTaskRequest(
    @field:NotBlank(message = "Title cannot be empty.")
    val title: String,
    val description: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val deadlineAt: Instant? = null,
    val reminderAt: Instant? = null,
    val category: TaskCategory? = null,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val completed: Boolean = false
)

data class TaskResponse(
    val id: String,
    val title: String,
    val description: String?,
    val priority: TaskPriority,
    val deadlineAt: Instant?,
    val reminderAt: Instant?,
    val category: TaskCategory,
    val recurrence: TaskRecurrence,
    val completed: Boolean,
    val completedAt: Instant?,
    val sourceTaskId: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
