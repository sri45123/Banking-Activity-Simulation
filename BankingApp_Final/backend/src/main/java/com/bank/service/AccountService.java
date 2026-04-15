package com.bank.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.exceptions.AccountNotFoundException;
import com.bank.exceptions.InvalidAmountException;
import com.bank.model.Account;
import com.bank.repository.AccountJpaRepository;

@Service
public class AccountService {

	private static final AtomicLong ACCOUNT_COUNTER = new AtomicLong(10000000L);

	private final AccountJpaRepository repo;

	public AccountService(AccountJpaRepository repo) {
		this.repo = repo;
	}

	@PostConstruct
	public void initializeCounter() {
		repo.findAll().stream()
			.map(Account::getAccountNumber)
			.filter(value -> value != null && value.matches("\\d+"))
			.mapToLong(Long::parseLong)
			.max()
			.ifPresent(max -> ACCOUNT_COUNTER.set(max + 1));
	}

	@Transactional
	public Account createAccount(String name, String email, BigDecimal openingBalance) throws InvalidAmountException {
		BigDecimal balance = openingBalance == null ? BigDecimal.ZERO : openingBalance;
		if (balance.compareTo(BigDecimal.ZERO) < 0) {
			throw new InvalidAmountException("Opening balance should not be Negative");
		}

		String normalizedEmail = email == null ? "" : email.trim();
		if (repo.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
			throw new IllegalArgumentException("An account already exists for email: " + normalizedEmail);
		}

		Account acc = new Account(name, normalizedEmail, balance);
		acc.setAccountNumber(String.valueOf(ACCOUNT_COUNTER.getAndIncrement()));
		return repo.save(acc);
	}

	@Transactional
	public Account saveAccount(Account account) {
		return repo.save(account);
	}

	@Transactional(readOnly = true)
	public Account getOrCreateAccountByEmail(String name, String email, BigDecimal openingBalance) {
		Account existing = repo.findByEmailIgnoreCase(email).orElse(null);
		if (existing != null) {
			return existing;
		}
		try {
			return createAccount(name, email, openingBalance == null ? BigDecimal.ZERO : openingBalance);
		} catch (InvalidAmountException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Transactional(readOnly = true)
	public Account getAccount(String accNo) throws AccountNotFoundException {
		Account account = repo.findById(accNo).orElse(null);

		if (account == null) {
			throw new AccountNotFoundException("Account not found: " + accNo);
		}

		return account;
	}

	@Transactional(readOnly = true)
	public Collection<Account> listAllAccounts() {
		return repo.findAll();
	}

	@Transactional(readOnly = true)
	public List<Account> searchAccounts(String name, String email) {
		if (email != null && !email.isBlank()) {
			return repo.findByEmailIgnoreCase(email.trim())
				.map(List::of)
				.orElseGet(List::of);
		}

		if (name != null && !name.isBlank()) {
			return repo.findByHolderNameContainingIgnoreCase(name.trim());
		}

		return repo.findAll();
	}
}


