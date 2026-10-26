package com.periferia.pricing.common.web;

import com.periferia.pricing.productrule.exception.DuplicateSkuException;
import com.periferia.pricing.productrule.exception.ProductRuleNotFoundException;
import com.periferia.pricing.productrule.exception.ProductRuleValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ProductRuleValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(ProductRuleValidationException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Datos inválidos",
                exception.getMessage());
        problem.setProperty("errors", Map.of(exception.getField(), exception.getMessage()));
        return response(problem);
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(DuplicateSkuException exception) {
        return response(problem(HttpStatus.CONFLICT, "SKU duplicado", exception.getMessage()));
    }

    @ExceptionHandler(ProductRuleNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ProductRuleNotFoundException exception) {
        return response(problem(HttpStatus.NOT_FOUND, "Producto inexistente", exception.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "Revisa los campos obligatorios de la solicitud.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "JSON inválido",
                "El cuerpo debe ser un objeto JSON válido, con los tipos de datos esperados.");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ProblemDetail> handleDatabaseUnavailable(Exception exception) {
        LOGGER.warn("No fue posible acceder a la base de datos.", exception);
        return response(problem(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                "No fue posible acceder a los productos. Intenta nuevamente más tarde."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        LOGGER.error("Error inesperado al procesar la solicitud.", exception);
        return response(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "No fue posible completar la solicitud."));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
