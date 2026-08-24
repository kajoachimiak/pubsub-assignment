package com.pubsub.assignment.exception;

import com.pubsub.assignment.model.json.ErrorResponse;
import com.pubsub.assignment.model.json.InputMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidBase64Exception.class)
    public ResponseEntity<ErrorResponse> handleInvalidBase64(InvalidBase64Exception ex) {
        log.warn("Invalid Base64 format for messageId: {}", ex.getMessageId());
        ErrorResponse response = new ErrorResponse("InvalidBase64", ex.getMessage(), ex.getMessageId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(InvalidXmlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidXml(InvalidXmlException ex) {
        log.warn("XML parsing failed for messageId: {}. Reason: {}", ex.getMessageId(), ex.getCause().getMessage());
        ErrorResponse response = new ErrorResponse("InvalidXml", ex.getMessage(), ex.getMessageId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MissingFieldException.class)
    public ResponseEntity<ErrorResponse> handleMissingField(MissingFieldException ex) {
        log.warn("Validation error for messageId: {}. Reason: {}", ex.getMessageId(), ex.getMessage());
        ErrorResponse response = new ErrorResponse("ValidationError", ex.getMessage(), ex.getMessageId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleSpringValidation(MethodArgumentNotValidException ex) {
        String messageId = extractMessageId(ex);
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request payload.");

        log.warn("Payload validation failed for messageId: {}. Reason: {}", messageId, errorMessage);
        ErrorResponse response = new ErrorResponse("ValidationError", errorMessage, messageId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        // Jeśli nie znamy messageId w tym miejscu, wysyłamy "unknown"
        ErrorResponse response = new ErrorResponse("ServerError", "An unexpected error occurred.", "unknown");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String extractMessageId(MethodArgumentNotValidException ex) {
        if (ex.getBindingResult().getTarget() instanceof InputMessage inputMessage) {
            return inputMessage.getMessageId() != null ? inputMessage.getMessageId() : "missing";
        }
        return "unknown";
    }
}