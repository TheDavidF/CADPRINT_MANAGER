package gt.cadprint.manager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    record ErrorResponse(String error) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Datos inválidos");
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> malformedRequest() {
        return ResponseEntity.badRequest().body(new ErrorResponse("Formato de solicitud inválido"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> uploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("La imagen supera el tamaño permitido"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> dataConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Conflicto de datos. Verifica SKU o referencias."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> operationError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).headers(exception.getHeaders())
                .body(new ErrorResponse(exception.getReason()));
    }
}