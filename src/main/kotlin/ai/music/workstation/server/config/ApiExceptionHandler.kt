package ai.music.workstation.server.config

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.servlet.resource.NoResourceFoundException

data class ErrorResponse(val error: String)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NoResourceFoundException::class)
    fun notFound(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message ?: "Resource not found"))

    @ExceptionHandler(IllegalArgumentException::class, HttpMessageNotReadableException::class)
    fun badRequest(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message ?: "Invalid request"))

    @ExceptionHandler(Exception::class)
    fun internal(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(ex.message ?: "Internal server error"))
}
