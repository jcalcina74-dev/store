package com.store.procesos.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  private final HttpStatus status;

  public ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public static ApiException notFound(String recurso) {
    return new ApiException(HttpStatus.NOT_FOUND, recurso + " no encontrado");
  }

  public static ApiException conflict(String detalle) {
    return new ApiException(HttpStatus.CONFLICT, detalle);
  }

  public static ApiException badRequest(String detalle) {
    return new ApiException(HttpStatus.BAD_REQUEST, detalle);
  }
}
