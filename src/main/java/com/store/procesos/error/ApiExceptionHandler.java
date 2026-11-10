package com.store.procesos.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.*;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String detail, HttpServletRequest request) {
    var body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(status).body(body);
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> api(ApiException e, HttpServletRequest r) {
    return problem(e.getStatus(), e.getMessage(), r);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(
      MethodArgumentNotValidException e, HttpServletRequest r) {
    var response = problem(HttpStatus.BAD_REQUEST, "Revisa los campos enviados", r);
    response
        .getBody()
        .setProperty(
            "errores",
            e.getBindingResult().getFieldErrors().stream()
                .map(
                    x ->
                        Map.of(
                            "campo",
                            x.getField(),
                            "mensaje",
                            String.valueOf(x.getDefaultMessage())))
                .collect(Collectors.toList()));
    return response;
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class,
    HandlerMethodValidationException.class,
    MissingServletRequestParameterException.class,
    MissingServletRequestPartException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception e, HttpServletRequest r) {
    return problem(HttpStatus.BAD_REQUEST, "Parametros o cuerpo de solicitud invalidos", r);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> tooLarge(Exception e, HttpServletRequest r) {
    return problem(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen supera el limite de 5 MiB", r);
  }

  @ExceptionHandler(MultipartException.class)
  ResponseEntity<ProblemDetail> multipart(Exception e, HttpServletRequest r) {
    return problem(HttpStatus.BAD_REQUEST, "Solicitud multipart invalida", r);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(Exception e, HttpServletRequest r) {
    return problem(HttpStatus.CONFLICT, "Registro duplicado o relacion que impide la operacion", r);
  }

  @ExceptionHandler({
    CannotAcquireLockException.class,
    ObjectOptimisticLockingFailureException.class
  })
  ResponseEntity<ProblemDetail> locked(Exception e, HttpServletRequest r) {
    return problem(HttpStatus.CONFLICT, "Operacion concurrente; vuelve a intentarlo", r);
  }

  // Una conexion SSE que se cierra no admite otra respuesta ni representa un error del CRUD.
  @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
  void clienteDesconectado() {}

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest r) {
    if (e instanceof org.springframework.web.ErrorResponse error)
      return ResponseEntity.status(error.getStatusCode()).body(error.getBody());
    log.error("Error interno en {}", r.getRequestURI(), e);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la operacion", r);
  }
}
