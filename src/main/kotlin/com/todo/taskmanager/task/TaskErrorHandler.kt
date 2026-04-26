package com.todo.taskmanager.task

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import javax.servlet.http.HttpServletRequest

@RestControllerAdvice
class TaskErrorHandler {

    @ExceptionHandler(TaskNotFoundException::class)
    fun handleNotFound(exception: TaskNotFoundException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(
                timestamp = Instant.now(),
                status = HttpStatus.NOT_FOUND.value(),
                error = HttpStatus.NOT_FOUND.reasonPhrase,
                message = exception.message ?: "Task not found.",
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiError> {
        val details = exception.bindingResult.fieldErrors.associate { fieldError ->
            fieldError.field to (fieldError.defaultMessage ?: "Invalid value.")
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiError(
                timestamp = Instant.now(),
                status = HttpStatus.BAD_REQUEST.value(),
                error = HttpStatus.BAD_REQUEST.reasonPhrase,
                message = "Submitted data is invalid.",
                path = request.requestURI,
                details = details
            )
        )
    }
}

data class ApiError(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val details: Map<String, String>? = null
)
