package com.hackathon.gdg.global.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
		return ResponseEntity
				.status(exception.getStatus())
				.body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError error : exception.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return ResponseEntity.badRequest().body(ApiErrorResponse.validation(fieldErrors));
	}

	@ExceptionHandler({
			HttpMessageNotReadableException.class,
			MethodArgumentTypeMismatchException.class,
			MissingServletRequestPartException.class
	})
	ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception) {
		return ResponseEntity.badRequest().body(ApiErrorResponse.of(
				ErrorCode.INVALID_REQUEST,
				"요청 형식을 확인해 주세요."
		));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiErrorResponse> handleUploadSize(MaxUploadSizeExceededException exception) {
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(ApiErrorResponse.of(
				ErrorCode.INVALID_IMAGE,
				"업로드 이미지 크기 제한을 초과했습니다."
		));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
		log.error("Unexpected API error", exception);
		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."));
	}
}
