package com.stockmonitor.stock_portfolio_monitoring_app.exception;


import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler{

//    Resource Not Found 404 Exception
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex , HttpServletRequest request
    ){
        log.warn("Resource Not Found : {} ", ex.getMessage());

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI(),
                null);
    }

//    Email Not Verified Exception 403

    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotVerified(
            AccountNotVerifiedException ex,
            HttpServletRequest request
    ){
        log.warn("Account is Not Verified : {} " , ex.getMessage());

       return buildErrorResponse(
               HttpStatus.FORBIDDEN,
               ex.getMessage(),
               request.getRequestURI(),
               null
               );
    }


//    Handle Inssufficient Quantity 400
    @ExceptionHandler(InsufficientQuantityException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientQuantity(
            InsufficientQuantityException ex ,
            HttpServletRequest request
    ){
        log.warn(" Insufficient Balance : {} ", ex.getMessage());

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI(),
                null
                );
    }
// Handle Security Exception 403
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(
            SecurityException ex,
            HttpServletRequest request
    ){
        log.warn("Security Violation : {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
    }

//    Handle Bad Credentials 401
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredential(
            BadCredentialsException ex,
            HttpServletRequest request
    ){
        log.warn("Authentication Failed : {} ",ex.getMessage());
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
    }

//    Handle IllegalArgument 400
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex ,
            HttpServletRequest request
    ){
        log.warn("Validation error : {} " , ex.getMessage());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
    }

//    Handle @Valid Bean Validation 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ){
        Map<String , String> errors = new HashMap<>();
        for(FieldError error : ex.getBindingResult().getFieldErrors()){
            errors.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("Request Body Validation Failed on {}: {} ", request.getRequestURI(), errors);
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed for one or more fields",
                request.getRequestURI(),
                errors
        );
    }



//    Handle Unexpected Exception 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            HttpServletRequest request) {
        log.error("Unhandled internal server error on {}: ", request.getRequestURI(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred. Please try again later.",
                request.getRequestURI(),
                null);
    }




//    Helper Mehtod to build the Exception Response
    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message ,
            String path ,
            Map<String , String> validationErrors
            ){
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timeStamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .validationErrors(validationErrors)
                .build();

        return new ResponseEntity<>(errorResponse , status);
    }
}
