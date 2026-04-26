package com.todo.taskmanager.task

class TaskNotFoundException(id: String) : RuntimeException("Task not found: $id")
