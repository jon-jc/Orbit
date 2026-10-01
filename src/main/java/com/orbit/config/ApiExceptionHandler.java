package com.orbit.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG=LoggerFactory.getLogger(ApiExceptionHandler.class);
    private ProblemDetail problem(HttpStatus status,String detail,HttpServletRequest request) {
        var problem=ProblemDetail.forStatusAndDetail(status,detail);
        problem.setProperty("requestId",request.getAttribute("requestId")); return problem;
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> status(ResponseStatusException ex,HttpServletRequest request) {
        HttpStatus status=HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(problem(status,ex.getReason()==null?status.getReasonPhrase():ex.getReason(),request));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex,HttpServletRequest request) {
        var errors=new LinkedHashMap<String,String>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(),error.getDefaultMessage()));
        var body=problem(HttpStatus.BAD_REQUEST,"Please check the highlighted fields.",request); body.setProperty("errors",errors);
        return ResponseEntity.badRequest().body(body);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> malformed(Exception ex,HttpServletRequest request) {
        for (Throwable cause=ex;cause!=null;cause=cause.getCause())
            if (cause instanceof RequestBodyLimitFilter.PayloadTooLargeException)
                return ResponseEntity.status(413).body(problem(HttpStatus.PAYLOAD_TOO_LARGE,"Request body must not exceed 64 KiB.",request));
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST,"The request contains invalid or missing values.",request));
    }
    @ExceptionHandler({NoResourceFoundException.class,HttpRequestMethodNotSupportedException.class,MissingServletRequestParameterException.class,
            HttpMediaTypeNotAcceptableException.class,HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ProblemDetail> framework(Exception ex,HttpServletRequest request) {
        var error=(ErrorResponse)ex;
        var status=HttpStatus.valueOf(error.getStatusCode().value());
        return ResponseEntity.status(status).headers(error.getHeaders()).body(problem(status,status.getReasonPhrase(),request));
    }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> authentication(AuthenticationException ex,HttpServletRequest request) {
        return ResponseEntity.status(401).body(problem(HttpStatus.UNAUTHORIZED,"Invalid email or password.",request));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(AccessDeniedException ex,HttpServletRequest request) {
        return ResponseEntity.status(403).body(problem(HttpStatus.FORBIDDEN,"You do not have permission to perform this action.",request));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException ex,HttpServletRequest request) {
        return ResponseEntity.status(409).body(problem(HttpStatus.CONFLICT,"The change conflicts with existing data. Refresh and try again.",request));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex,HttpServletRequest request) {
        LOG.error("Request {} failed",request.getAttribute("requestId"),ex);
        return ResponseEntity.internalServerError().body(problem(HttpStatus.INTERNAL_SERVER_ERROR,"Something went wrong. Please try again and include the request ID if the problem persists.",request));
    }
}
