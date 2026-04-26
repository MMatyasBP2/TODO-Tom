package com.todo.taskmanager.task

import org.springframework.stereotype.Service
import java.time.Instant

@Service
class TaskService(
    private val repository: TaskRepository
) {

    fun findAll(): List<TaskResponse> = repository.findAll().map { it.toResponse() }

    fun findById(id: String): TaskResponse = repository.findById(id)
        .orElseThrow { TaskNotFoundException(id) }
        .toResponse()

    fun create(request: CreateTaskRequest): TaskResponse {
        val normalizedTitle = request.title.trim()
        val normalizedDescription = request.description?.trim()?.takeIf { it.isNotEmpty() }
        val category = request.category ?: autoCategory(normalizedTitle, normalizedDescription)

        val task = Task(
            title = normalizedTitle,
            description = normalizedDescription,
            priority = request.priority,
            deadlineAt = request.deadlineAt,
            reminderAt = request.reminderAt,
            category = category,
            recurrence = request.recurrence
        )

        return repository.save(task).toResponse()
    }

    fun update(id: String, request: UpdateTaskRequest): TaskResponse {
        val existing = repository.findById(id)
            .orElseThrow { TaskNotFoundException(id) }

        val normalizedTitle = request.title.trim()
        val normalizedDescription = request.description?.trim()?.takeIf { it.isNotEmpty() }
        val category = request.category ?: autoCategory(normalizedTitle, normalizedDescription)
        val completedAt = when {
            !existing.completed && request.completed -> Instant.now()
            existing.completed && !request.completed -> null
            else -> existing.completedAt
        }

        val updated = existing.copy(
            title = normalizedTitle,
            description = normalizedDescription,
            priority = request.priority,
            deadlineAt = request.deadlineAt,
            reminderAt = request.reminderAt,
            category = category,
            recurrence = request.recurrence,
            completed = request.completed,
            completedAt = completedAt,
            lastRecurrenceGeneratedAt = if (!request.completed) null else existing.lastRecurrenceGeneratedAt
        )

        return repository.save(updated).toResponse()
    }

    fun delete(id: String) {
        if (!repository.existsById(id)) {
            throw TaskNotFoundException(id)
        }

        repository.deleteById(id)
    }

    private fun Task.toResponse(): TaskResponse = TaskResponse(
        id = requireNotNull(id),
        title = title,
        description = description,
        priority = priority,
        deadlineAt = deadlineAt,
        reminderAt = reminderAt,
        category = category,
        recurrence = recurrence,
        completed = completed,
        completedAt = completedAt,
        sourceTaskId = sourceTaskId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun autoCategory(title: String, description: String?): TaskCategory {
        val text = "$title ${description ?: ""}".lowercase()

        val shoppingKeywords = listOf("bevas", "vasar", "bolt", "kenyer", "tej", "bevasarlas")
        val workKeywords = listOf("munka", "meeting", "projekt", "ugyfel", "hatarido")
        val universityKeywords = listOf("egyetem", "vizsga", "zh", "beadando", "ora", "tanul")

        return when {
            shoppingKeywords.any { text.contains(it) } -> TaskCategory.SHOPPING
            workKeywords.any { text.contains(it) } -> TaskCategory.WORK
            universityKeywords.any { text.contains(it) } -> TaskCategory.UNIVERSITY
            else -> TaskCategory.PERSONAL
        }
    }
}
