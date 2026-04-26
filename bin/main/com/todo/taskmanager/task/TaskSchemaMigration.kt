package com.todo.taskmanager.task

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class TaskSchemaMigration(
    private val jdbcTemplate: JdbcTemplate
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        val tableExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'tasks'",
            Int::class.java
        ) == 1

        if (!tableExists) {
            return
        }

        val columns = jdbcTemplate.queryForList("PRAGMA table_info(tasks)")
            .mapNotNull { it["name"] as? String }
            .toSet()

        addColumnIfMissing(columns, "priority", "TEXT NOT NULL DEFAULT 'MEDIUM'")
        addColumnIfMissing(columns, "deadline_at", "DATETIME")
        addColumnIfMissing(columns, "reminder_at", "DATETIME")
        addColumnIfMissing(columns, "category", "TEXT NOT NULL DEFAULT 'PERSONAL'")
        addColumnIfMissing(columns, "recurrence", "TEXT NOT NULL DEFAULT 'NONE'")
        addColumnIfMissing(columns, "completed_at", "DATETIME")
        addColumnIfMissing(columns, "source_task_id", "TEXT")
        addColumnIfMissing(columns, "last_recurrence_generated_at", "DATETIME")
    }

    private fun addColumnIfMissing(existingColumns: Set<String>, columnName: String, columnSqlType: String) {
        if (existingColumns.contains(columnName)) {
            return
        }

        jdbcTemplate.execute("ALTER TABLE tasks ADD COLUMN $columnName $columnSqlType")
    }
}
