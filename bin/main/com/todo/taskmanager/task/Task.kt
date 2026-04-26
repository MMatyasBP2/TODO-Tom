package com.todo.taskmanager.task

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "tasks")
data class Task(
    @Id
    @Column(nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),
    @Column(nullable = false)
    val title: String,
    @Column
    val description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column
    val priority: TaskPriority = TaskPriority.MEDIUM,
    @Column
    val deadlineAt: Instant? = null,
    @Column
    val reminderAt: Instant? = null,
    @Enumerated(EnumType.STRING)
    @Column
    val category: TaskCategory = TaskCategory.PERSONAL,
    @Enumerated(EnumType.STRING)
    @Column
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    @Column(nullable = false)
    val completed: Boolean = false,
    @Column
    val completedAt: Instant? = null,
    @Column
    val sourceTaskId: String? = null,
    @Column
    val lastRecurrenceGeneratedAt: Instant? = null,
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null,
    @UpdateTimestamp
    @Column(nullable = false)
    var updatedAt: Instant? = null
)

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
