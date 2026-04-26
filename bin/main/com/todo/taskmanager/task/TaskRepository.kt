package com.todo.taskmanager.task

import org.springframework.data.jpa.repository.JpaRepository

interface TaskRepository : JpaRepository<Task, String> {
	fun findByCompletedTrueAndRecurrenceNot(recurrence: TaskRecurrence): List<Task>
}
