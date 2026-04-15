package com.bank.controller;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.dto.ApiResponse;
import com.bank.util.FileReportUtil;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

	@GetMapping("/overview")
	public ResponseEntity<ApiResponse<Map<String, Object>>> overview(@RequestParam(value = "accNo", required = false) String accNo) {
		List<HistoryItem> items = FileReportUtil.readAllLines().stream()
			.map(AnalyticsController::parseHistoryLine)
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
