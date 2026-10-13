package com.smim.server.common;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handle(ApiException e) {
		return ResponseEntity.status(e.code().status()).body(ErrorResponse.of(e.code()));
	}

	// 본문 검증 · 쿼리 파라미터 검증 · 형식 오류(id=abc) 를 모두 같은 400 으로
	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
			HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class })
	ResponseEntity<ErrorResponse> handleInvalid(Exception e) {
		return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
	}
}
