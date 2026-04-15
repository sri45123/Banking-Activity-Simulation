package com.bank.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import com.bank.dto.ApiResponse;
import com.bank.dto.TransactionRequest;
import com.bank.dto.TransferRequest;
import com.bank.service.TransactionService;
import com.bank.util.FileReportUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

	private final TransactionService transactionService;

	public TransactionController(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	@PostMapping({"/deposite", "/deposit"})
	public ResponseEntity<ApiResponse<Map<String, String>>> deposit(@RequestAttribute("authenticatedEmail") String authenticatedEmail,
			@Valid @RequestBody TransactionRequest request) throws Exception {
		transactionService.deposite(request.accNo, request.amount);
		return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Deposit successful", Map.of("accountNumber", request.accNo)));
	}

	@PostMapping({"/withdraw", "/tranctions/withdraw"})
	public ResponseEntity<ApiResponse<Map<String, String>>> withdraw(@RequestAttribute("authenticatedEmail") String authenticatedEmail,
			@Valid @RequestBody TransactionRequest request) throws Exception {
		transactionService.withdraw(request.accNo, request.amount);
		return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Withdraw successful", Map.of("accountNumber", request.accNo)));
	}

	@PostMapping("/transfer")
	public ResponseEntity<ApiResponse<Map<String, String>>> transfer(@RequestAttribute("authenticatedEmail") String authenticatedEmail,
			@Valid @RequestBody TransferRequest request) throws Exception {
		transactionService.transfer(request.fromAcc, request.toAcc, request.amount);
		return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Transfer transaction successful", Map.of("fromAcc", request.fromAcc, "toAcc", request.toAcc)));
	}

	@GetMapping("/history")
	public ResponseEntity<ApiResponse<List<String>>> history() {
		return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Transaction history fetched successfully", FileReportUtil.readAllLines()));
	}

	@GetMapping("/history/{accNo}")
	public ResponseEntity<ApiResponse<List<String>>> historyForAccount(@PathVariable("accNo") String accNo) {
		List<String> filtered = FileReportUtil.readAllLines().stream()
			.filter(line -> line.contains("Acc: " + accNo) || line.contains("to " + accNo))
			.toList();
		return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Transaction history fetched successfully", filtered));
	}

	@GetMapping("/query-history")
	public ResponseEntity<ApiResponse<List<String>>> queryHistory(@RequestParam(value = "accNo", required = false) String accNo,
			@RequestParam(value = "type", required = false) String type,
			@RequestParam(value = "minAmount", required = false) String minAmount,
			@RequestParam(value = "maxAmount", required = false) String maxAmount,
			@RequestParam(value = "from", required = false) String from,
			@RequestParam(value = "to", required = false) String to) {
		Double min = parseDouble(minAmount);
		Double max = parseDouble(maxAmount);
		LocalDate fromDate = parseDate(from);
		LocalDate toDate = parseDate(to);

		List<String> filtered = FileReportUtil.readAllLines().stream()
			.map(TransactionController::parseHistoryLine)
			.filter(item -> item != null)
			.filter(item -> accNo == null || accNo.isBlank() || item.accNo.equals(accNo) || (item.targetAcc != null && item.targetAcc.equals(accNo)))
			.filter(item -> type == null || type.isBlank() || item.type.equalsIgnoreCase(type))
			.filter(item -> min == null || item.amount >= min)
			.filter(item -> max == null || item.amount <= max)
			.filter(item -> {
				if (fromDate == null && toDate == null) {
					return true;
				}
				if (item.timestamp == null) {
					return false;
				}
				LocalDate d = item.timestamp.toLocalDate();
				if (fromDate != null && d.isBefore(fromDate)) {
					return false;
				}
				if (toDate != null && d.isAfter(toDate)) {
					return false;
				}
				return true;
			})
			.map(item -> item.raw)
			.toList();

		return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Filtered history fetched successfully", filtered));
	}

	@GetMapping("/analytics/overview")
	public ResponseEntity<ApiResponse<Map<String, Object>>> overview(@RequestParam(value = "accNo", required = false) String accNo) {
		List<HistoryItem> items = FileReportUtil.readAllLines().stream()
			.map(TransactionController::parseHistoryLine)
			.filter(item -> item != null)
			.filter(item -> accNo == null || accNo.isBlank() || item.accNo.equals(accNo) || (item.targetAcc != null && item.targetAcc.equals(accNo)))
			.toList();

		double totalDeposit = items.stream().filter(i -> "DEPOSITE".equalsIgnoreCase(i.type)).mapToDouble(i -> i.amount).sum();
		double totalWithdraw = items.stream().filter(i -> "WITHDRAW".equalsIgnoreCase(i.type)).mapToDouble(i -> i.amount).sum();
		double totalTransfer = items.stream().filter(i -> "TRANSFER".equalsIgnoreCase(i.type)).mapToDouble(i -> i.amount).sum();

		Map<String, Double> monthlySpending = new LinkedHashMap<>();
		for (HistoryItem item : items) {
			if (item.timestamp == null) {
				continue;
			}
			if (!"WITHDRAW".equalsIgnoreCase(item.type) && !"TRANSFER".equalsIgnoreCase(item.type)) {
				continue;
			}
			String month = YearMonth.from(item.timestamp).toString();
			monthlySpending.put(month, monthlySpending.getOrDefault(month, 0.0) + item.amount);
		}

		List<String> recent = items.stream()
			.sorted(Comparator.comparing((HistoryItem i) -> i.timestamp == null ? LocalDateTime.MIN : i.timestamp).reversed())
			.limit(8)
			.map(i -> i.raw)
			.toList();

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("totalDeposit", totalDeposit);
		payload.put("totalWithdraw", totalWithdraw);
		payload.put("totalTransfer", totalTransfer);
		payload.put("monthlySpending", monthlySpending);
		payload.put("recentTransactions", recent);
		return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Analytics overview fetched successfully", payload));
	}

	private static Double parseDouble(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Double.parseDouble(value);
		} catch (Exception ex) {
			return null;
		}
	}

	private static LocalDate parseDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		} catch (Exception ex) {
			return null;
		}
	}

	private static HistoryItem parseHistoryLine(String line) {
		if (line == null || line.isBlank()) {
			return null;
		}
		try {
			String[] parts = line.split("\\|\\s*");
			if (parts.length < 2) {
				return null;
			}
			LocalDateTime timestamp = LocalDateTime.parse(parts[0].trim());
			String type = parts[1].trim();
			String accNo = extractSegment(line, "Acc:");
			String amountStr = extractSegment(line, "Amount:");
			String targetAcc = extractTargetAccount(line);
			double amount = amountStr == null ? 0.0 : Double.parseDouble(amountStr);
			return new HistoryItem(line, timestamp, type, accNo, targetAcc, amount);
		} catch (Exception ex) {
			return null;
		}
	}

	private static String extractSegment(String line, String key) {
		int idx = line.indexOf(key);
		if (idx < 0) {
			return null;
		}
		int start = idx + key.length();
		int end = line.indexOf('|', start);
		String segment = end < 0 ? line.substring(start) : line.substring(start, end);
		return segment.trim();
	}

	private static String extractTargetAccount(String line) {
		int idx = line.lastIndexOf("to ");
		if (idx < 0) {
			return null;
		}
		return line.substring(idx + 3).trim();
	}

	private static final class HistoryItem {
		private final String raw;
		private final LocalDateTime timestamp;
		private final String type;
		private final String accNo;
		private final String targetAcc;
		private final double amount;

		private HistoryItem(String raw, LocalDateTime timestamp, String type, String accNo, String targetAcc, double amount) {
			this.raw = raw;
			this.timestamp = timestamp;
			this.type = type;
			this.accNo = accNo;
			this.targetAcc = targetAcc;
			this.amount = amount;
		}
	}
}