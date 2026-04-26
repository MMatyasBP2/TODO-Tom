package com.todo.taskmanager.task

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TaskRecurrenceScheduler(
    private val repository: TaskRepository
) {

    @Scheduled(fixedDelay = 60000)
    fun generateRecurringTasks() {
        val now = Instant.now()
        val recurringCompleted = repository.findByCompletedTrueAndRecurrenceNot(TaskRecurrence.NONE)

        recurringCompleted.forEach { task ->
            val completedAt = task.completedAt ?: return@forEach
            val alreadyGeneratedForCompletion = task.lastRecurrenceGeneratedAt?.isAfter(completedAt) == true
            if (alreadyGeneratedForCompletion) {
                return@forEach
            }

            val nextDeadline = task.deadlineAt?.let { shiftByRecurrence(it, task.recurrence) }
            val nextReminder = task.reminderAt?.let { shiftByRecurrence(it, task.recurrence) }

            val nextTask = Task(
                title = task.title,
                description = task.description,
                priority = task.priority,
                deadlineAt = nextDeadline,
                reminderAt = nextReminder,
                category = task.category,
                recurrence = task.recurrence,
                completed = false,
                completedAt = null,
                sourceTaskId = task.id,
                lastRecurrenceGeneratedAt = null
            )

            repository.save(nextTask)
            repository.save(task.copy(lastRecurrenceGeneratedAt = now))
        }
    }

    private fun shiftByRecurrence(source: Instant, recurrence: TaskRecurrence): Instant {
        return when (recurrence) {
            TaskRecurrence.DAILY -> source.plusSeconds(24 * 60 * 60)
            TaskRecurrence.WEEKLY -> source.plusSeconds(7 * 24 * 60 * 60)
            TaskRecurrence.MONTHLY -> source.plusSeconds(30L * 24 * 60 * 60)
            TaskRecurrence.NONE -> source
        }
    }
}
