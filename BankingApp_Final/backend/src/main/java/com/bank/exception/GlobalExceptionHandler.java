package com.bank.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.bank.dto.ApiResponse;
import com.bank.exceptions.AccountNotFoundException;
import com.bank.exceptions.InsufficientBalanceException;
import com.bank.exceptions.InvalidAmountException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AccountNotFoundException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleAccountNotFound(AccountNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(ex.getMessage()));
	}

	@ExceptionHandler({InvalidAmountException.class, InsufficientBalanceException.class, IllegalArgumentException.class})
	public ResponseEntity<ApiResponse<Map<String, String>>> handleBadRequest(Exception ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
			.orElse("Validation failed");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(message));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleDataIntegrity(DataIntegrityViolationException ex) {
		String raw = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
		String lower = raw == null ? "" : raw.toLowerCase();
		if (lower.contains("accounts") && lower.contains("email") && (lower.contains("unique") || lower.contains("constraint"))) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("An account with this email already exists"));
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("Could not persist data due to a constraint violation"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleUnexpected(Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(ex.getMessage() == null ? "Request failed" : ex.getMessage()));
	}

	private ApiResponse<Map<String, String>> error(String message) {
		Map<String, String> payload = new LinkedHashMap<>();
		payload.put("detail", message);
		return new ApiResponse<>("ERROR", message, payload);
	}
}