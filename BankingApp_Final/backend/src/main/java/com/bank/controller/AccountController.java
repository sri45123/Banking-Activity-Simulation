package com.bank.controller;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bank.dto.AccountDTO;
import com.bank.dto.ApiResponse;
import com.bank.model.Account;
import com.bank.service.AccountService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/accounts")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@PostMapping("/create")
	public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestAttribute("authenticatedEmail") String authenticatedEmail,
			@Valid @RequestBody AccountDTO request) throws Exception {
		Account account = accountService.createAccount(request.name, request.email, request.balance);
		return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Account created successfully", Map.of("accountNumber", account.getAccountNumber(), "account", account)));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<Account>> get(@PathVariable("id") String id) throws Exception {
		Account account = accountService.getAccount(id);
		return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Account fetched successfully", account));
	}

	@GetMapping("/all")
	public ResponseEntity<ApiResponse<Collection<Account>>> all() {
		return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Accounts fetched successfully", accountService.listAllAccounts()));
	}

	@GetMapping("/search")
	public ResponseEntity<ApiResponse<List<Account>>> search(@org.springframework.web.bind.annotation.RequestParam(value = "name", required = false) String name,
			@org.springframework.web.bind.annotation.RequestParam(value = "email", required = false) String email) {
		List<Account> matches = accountService.searchAccounts(name, email);
		String message = matches.isEmpty() ? "No matching accounts found" : "Accounts fetched successfully";
		return ResponseEntity.ok(new ApiResponse<>("SUCCESS", message, matches));
	}
}