const BASE_URL = "http://localhost:8080";
let authToken = localStorage.getItem("nb_auth_token") || "";
let authName = localStorage.getItem("nb_auth_name") || "";
let pinUnlocked = localStorage.getItem("nb_pin_unlocked") === "1";
let hasConfiguredPin = localStorage.getItem("nb_has_pin") === "1";
let dwChart = null;
let monthlyChart = null;

function showSection(id) {
    document.querySelectorAll(".section").forEach((sec) => {
        sec.style.display = "none";
    });
    document.getElementById(id).style.display = "block";
    if (id === "dashboard") {
        loadDashboard();
    }
}

function isValidAmount(value) {
    return Number.isFinite(value) && value > 0;
}

function notify(message, type) {
    const banner = document.getElementById("notification");
    banner.className = "notification " + (type || "success");
    banner.textContent = message;
    banner.classList.remove("hidden");
    pushToast(message, type || "success");
    browserNotify(message);
}

function pushToast(message, type) {
    const stack = document.getElementById("toast-stack");
    const toast = document.createElement("div");
    toast.className = "toast " + (type || "success");
    toast.textContent = message;
    stack.appendChild(toast);
    setTimeout(() => {
        toast.remove();
    }, 3200);
}

function browserNotify(message) {
    if (!("Notification" in window)) {
        return;
    }
    if (Notification.permission === "default") {
        Notification.requestPermission();
        return;
    }
    if (Notification.permission === "granted") {
        new Notification("NovaBank Alert", { body: message });
    }
}

function updateAuthStatus() {
    const status = document.getElementById("auth-status");
    if (authToken) {
        status.textContent = "Logged in as " + (authName || "User");
        status.classList.add("is-auth");
    } else {
        status.textContent = "Not logged in";
        status.classList.remove("is-auth");
    }
}

function clearLocalSession() {
    authToken = "";
    authName = "";
    pinUnlocked = false;
    hasConfiguredPin = false;
    localStorage.removeItem("nb_auth_token");
    localStorage.removeItem("nb_auth_name");
    localStorage.removeItem("nb_pin_unlocked");
    localStorage.removeItem("nb_has_pin");
}

function requireAuth() {
    if (authToken) {
        return true;
    }
    notify("Please login first to continue.", "error");
    showSection("auth");
    return false;
}

async function requirePinIfNeeded() {
    if (!requireAuth()) {
        return false;
    }
    if (!hasConfiguredPin || pinUnlocked) {
        return true;
    }
    openLock();
    notify("Enter your PIN to continue.", "error");
    return false;
}

function setLoading(active) {
    const indicator = document.getElementById("global-loading");
    indicator.classList.toggle("hidden", !active);
    document.querySelectorAll("button").forEach((btn) => {
        btn.disabled = active;
    });
}

function setResult(id, message, isError) {
    const node = document.getElementById(id);
    node.style.color = isError ? "#9f2020" : "#0f6d3a";
    node.textContent = message;
}

async function apiRequest(path, method, payload) {
    setLoading(true);
    try {
        const res = await fetch(BASE_URL + path, {
            method,
            headers: {
                "Content-Type": "application/json",
                "Authorization": authToken ? "Bearer " + authToken : ""
            },
            body: payload ? JSON.stringify(payload) : undefined
        });

        const text = await res.text();
        if (!res.ok) {
            try {
                const data = JSON.parse(text);
                if (res.status === 401 && (data.message || "").toLowerCase().includes("invalid or expired session")) {
                    clearLocalSession();
                    updateAuthStatus();
                    showSection("auth");
                    notify("Session expired. Please login again.", "error");
                }
                throw new Error(data.message || "Request failed");
            } catch (err) {
                if (err instanceof SyntaxError) {
                    throw new Error(text || "Request failed");
                }
                throw err;
            }
        }

        if (!text) {
            return {};
        }
        try {
            return JSON.parse(text);
        } catch (_err) {
            return text;
        }
    } finally {
        setLoading(false);
    }
}

async function registerUser() {
    const name = document.getElementById("r-name").value.trim();
    const username = document.getElementById("r-username").value.trim();
    const email = document.getElementById("r-email").value.trim();
    const password = document.getElementById("r-password").value;
    const pin = document.getElementById("r-pin").value.trim();

    if (!name || !username || !email || !password) {
        setResult("register-result", "Name, username, email and password are required.", true);
        notify("Registration failed: missing details.", "error");
        return;
    }

    if (pin && !/^\d{4,6}$/.test(pin)) {
        setResult("register-result", "PIN must be 4 to 6 digits.", true);
        notify("Registration failed: invalid PIN format.", "error");
        return;
    }

    try {
        const data = await apiRequest("/auth/register", "POST", { name, username, email, password, pin });
        const accountNumber = data.accountNumber || "";
        const message = accountNumber
            ? (data.message || "Registration successful.") + " Account Number: " + accountNumber
            : (data.message || "Registration successful.");
        setResult("register-result", message, false);
        notify(message, "success");
    } catch (err) {
        setResult("register-result", err.message, true);
        notify("Registration failed: " + err.message, "error");
    }
}

async function loginUser() {
    const username = document.getElementById("l-username").value.trim();
    const email = document.getElementById("l-email").value.trim();
    const password = document.getElementById("l-password").value;

    if ((!username && !email) || !password) {
        setResult("login-result", "Username/email and password are required.", true);
        notify("Login failed: missing credentials.", "error");
        return;
    }

    try {
        const data = await apiRequest("/auth/login", "POST", { username, email, password });
        authToken = data.token || "";
        authName = data.name || "";
        hasConfiguredPin = String(data.pinConfigured) === "true";
        pinUnlocked = !hasConfiguredPin;
        localStorage.setItem("nb_auth_token", authToken);
        localStorage.setItem("nb_auth_name", authName);
        localStorage.setItem("nb_has_pin", hasConfiguredPin ? "1" : "0");
        localStorage.setItem("nb_pin_unlocked", pinUnlocked ? "1" : "0");
        updateAuthStatus();
        setResult("login-result", data.message || "Login successful.", false);
        notify("Login successful.", "success");
        showSection("dashboard");
    } catch (err) {
        setResult("login-result", err.message, true);
        notify("Login failed: " + err.message, "error");
    }
}

async function requestPasswordResetCode() {
    const email = document.getElementById("fp-email").value.trim();
    if (!email) {
        setResult("forgot-result", "Please enter your registered email.", true);
        notify("Forgot password failed: email is required.", "error");
        return;
    }

    try {
        const data = await apiRequest("/auth/forgot-password", "POST", { email });
        setResult("forgot-result", data.message || "Reset code sent to your email.", false);
        notify("Reset code message sent to email.", "success");
    } catch (err) {
        setResult("forgot-result", err.message, true);
        notify("Forgot password failed: " + err.message, "error");
    }
}

async function resetForgottenPassword() {
    const email = document.getElementById("fp-email").value.trim();
    const code = document.getElementById("fp-code").value.trim();
    const newPassword = document.getElementById("fp-new-password").value;

    if (!email || !code || !newPassword) {
        setResult("forgot-result", "Email, reset code, and new password are required.", true);
        notify("Reset password failed: missing details.", "error");
        return;
    }

    if (!/^\d{6}$/.test(code)) {
        setResult("forgot-result", "Reset code must be 6 digits.", true);
        notify("Reset password failed: invalid code format.", "error");
        return;
    }

    if (newPassword.length < 4) {
        setResult("forgot-result", "Password must be at least 4 characters.", true);
        notify("Reset password failed: weak password.", "error");
        return;
    }

    try {
        const data = await apiRequest("/auth/reset-password", "POST", { email, code, newPassword });
        setResult("forgot-result", data.message || "Password reset successful.", false);
        notify("Password reset successful. Please login.", "success");
        document.getElementById("l-email").value = email;
        document.getElementById("l-password").value = "";
        document.getElementById("fp-code").value = "";
        document.getElementById("fp-new-password").value = "";
    } catch (err) {
        setResult("forgot-result", err.message, true);
        notify("Reset password failed: " + err.message, "error");
    }
}

async function logoutUser() {
    if (authToken) {
        try {
            await apiRequest("/auth/logout", "POST", {});
        } catch (_err) {
            // Ignore logout transport errors and clear local session anyway.
        }
    }

    clearLocalSession();
    updateAuthStatus();
    notify("Logged out successfully.", "success");
    showSection("auth");
}

async function savePin() {
    if (!requireAuth()) {
        return;
    }

    const pin = document.getElementById("p-pin").value.trim();
    if (!/^\d{4,6}$/.test(pin)) {
        setResult("pin-result", "PIN must be 4 to 6 digits.", true);
        notify("PIN save failed: invalid format.", "error");
        return;
    }

    try {
        const data = await apiRequest("/auth/set-pin", "POST", { pin });
        hasConfiguredPin = true;
        pinUnlocked = true;
        localStorage.setItem("nb_has_pin", "1");
        localStorage.setItem("nb_pin_unlocked", "1");
        setResult("pin-result", data.message || "PIN saved.", false);
        notify("PIN configured successfully.", "success");
    } catch (err) {
        setResult("pin-result", err.message, true);
        notify("PIN save failed: " + err.message, "error");
    }
}

async function createAccount() {
    if (!(await requirePinIfNeeded())) {
        return;
    }

    const name = document.getElementById("c-name").value.trim();
    const email = document.getElementById("c-email").value.trim();
    const balance = parseFloat(document.getElementById("c-balance").value);

    if (!name || !email) {
        setResult("create-result", "Name and email are required.", true);
        notify("Create account failed: missing details.", "error");
        return;
    }
    if (!isValidAmount(balance) && balance !== 0) {
        setResult("create-result", "Enter a valid opening balance.", true);
        notify("Create account failed: invalid opening balance.", "error");
        return;
    }

    try {
        const result = await apiRequest("/accounts/create", "POST", {
            name,
            email,
            balance: Number.isFinite(balance) ? balance : 0
        });
        const msg = "Account created successfully. Number: " + result.accountNumber;
        setResult("create-result", msg, false);
        notify(msg, "success");
    } catch (err) {
        setResult("create-result", err.message, true);
        notify("Create account failed: " + err.message, "error");
    }
}

async function deposit() {
    if (!(await requirePinIfNeeded())) {
        return;
    }

    const accNo = document.getElementById("d-acc").value.trim();
    const amount = parseFloat(document.getElementById("d-amount").value);
    if (!accNo || !isValidAmount(amount)) {
        setResult("deposit-result", "Provide valid account number and amount.", true);
        notify("Deposit failed due to invalid input.", "error");
        return;
    }

    try {
        const msg = await apiRequest("/transactions/deposit", "POST", { accNo, amount });
        setResult("deposit-result", msg, false);
        notify("Deposit completed successfully.", "success");
    } catch (err) {
        setResult("deposit-result", err.message, true);
        notify("Deposit failed: " + err.message, "error");
    }
}

async function withdraw() {
    if (!(await requirePinIfNeeded())) {
        return;
    }

    const accNo = document.getElementById("w-acc").value.trim();
    const amount = parseFloat(document.getElementById("w-amount").value);
    if (!accNo || !isValidAmount(amount)) {
        setResult("withdraw-result", "Provide valid account number and amount.", true);
        notify("Withdraw failed due to invalid input.", "error");
        return;
    }

    try {
        const msg = await apiRequest("/transactions/withdraw", "POST", { accNo, amount });
        setResult("withdraw-result", msg, false);
        notify("Withdraw completed successfully.", "success");
    } catch (err) {
        setResult("withdraw-result", err.message, true);
        notify("Withdraw failed: " + err.message, "error");
    }
}

async function transfer() {
    if (!(await requirePinIfNeeded())) {
        return;
    }

    const fromAcc = document.getElementById("t-from").value.trim();
    const toAcc = document.getElementById("t-to").value.trim();
    const amount = parseFloat(document.getElementById("t-amount").value);
    if (!fromAcc || !toAcc || !isValidAmount(amount)) {
        setResult("transfer-result", "Enter valid sender, receiver, and amount.", true);
        notify("Transfer failed due to invalid input.", "error");
        return;
    }
    if (fromAcc === toAcc) {
        setResult("transfer-result", "Sender and receiver cannot be same.", true);
        notify("Transfer failed: account numbers cannot match.", "error");
        return;
    }

    try {
        const msg = await apiRequest("/transactions/transfer", "POST", { fromAcc, toAcc, amount });
        setResult("transfer-result", msg, false);
        notify("Transfer completed successfully.", "success");
    } catch (err) {
        setResult("transfer-result", err.message, true);
        notify("Transfer failed: " + err.message, "error");
    }
}

async function viewAccount() {
    if (!(await requirePinIfNeeded())) {
        return;
    }

    const acc = document.getElementById("v-acc").value.trim();
    if (!acc) {
        notify("Please enter account number to view balance.", "error");
        return;
    }

    try {
        const data = await apiRequest("/accounts/" + encodeURIComponent(acc), "GET");
        const card = document.getElementById("view-result");
        card.classList.remove("hidden");
        card.innerHTML = [
            "<div><strong>Account Holder:</strong> " + data.holderName + "</div>",
            "<div><strong>Account Number:</strong> " + data.accountNumber + "</div>",
            "<div class='amount'>Current Balance: Rs " + data.openingBalance + "</div>"
        ].join("");
        notify("Balance loaded successfully.", "success");
    } catch (err) {
        document.getElementById("view-result").classList.add("hidden");
        notify("View balance failed: " + err.message, "error");
    }
}

async function loadHistory() {
    if (!(await requirePinIfNeeded())) {
        return;
    }

    const accNo = document.getElementById("h-acc").value.trim();
    const type = document.getElementById("h-type").value.trim();
    const min = document.getElementById("h-min").value.trim();
    const max = document.getElementById("h-max").value.trim();
    const from = document.getElementById("h-from").value;
    const to = document.getElementById("h-to").value;
    const target = document.getElementById("history-result");
    target.innerHTML = "";

    const params = new URLSearchParams();
    if (accNo) params.set("accNo", accNo);
    if (type) params.set("type", type);
    if (min) params.set("minAmount", min);
    if (max) params.set("maxAmount", max);
    if (from) params.set("from", from);
    if (to) params.set("to", to);

    try {
        const data = await apiRequest("/transactions/query-history?" + params.toString(), "GET");
        if (!Array.isArray(data) || data.length === 0) {
            target.innerHTML = "<li>No transactions found for selected filters.</li>";
            notify("No history entries found.", "error");
            return;
        }
        target.innerHTML = data.map((line) => "<li>" + line + "</li>").join("");
        notify("Transaction history loaded.", "success");
    } catch (err) {
        target.innerHTML = "<li>Failed to load history.</li>";
        notify("History load failed: " + err.message, "error");
    }
}

async function listAccounts() {
    if (!(await requirePinIfNeeded())) {
        return;
    }

    try {
        const data = await apiRequest("/accounts/all", "GET");
        document.getElementById("list-result").innerText = JSON.stringify(data, null, 2);
        notify("Accounts loaded.", "success");
    } catch (err) {
        document.getElementById("list-result").innerText = "[]";
        notify("Failed to load accounts: " + err.message, "error");
    }
}

async function loadDashboard() {
    if (!(await requirePinIfNeeded())) {
        return;
    }

    const accNo = document.getElementById("dash-acc").value.trim();
    const qs = accNo ? "?accNo=" + encodeURIComponent(accNo) : "";
    try {
        const response = await apiRequest("/transactions/analytics/overview" + qs, "GET");
        const data = response && typeof response === "object" && response.data ? response.data : response;

        document.getElementById("kpi-deposit").textContent = "Rs " + Number(data?.totalDeposit || 0).toFixed(2);
        document.getElementById("kpi-withdraw").textContent = "Rs " + Number(data?.totalWithdraw || 0).toFixed(2);
        document.getElementById("kpi-transfer").textContent = "Rs " + Number(data?.totalTransfer || 0).toFixed(2);

        renderDwChart(Number(data?.totalDeposit || 0), Number(data?.totalWithdraw || 0));
        renderMonthlyChart(data?.monthlySpending || {});

        const recent = Array.isArray(data?.recentTransactions) ? data.recentTransactions : [];
        document.getElementById("dash-recent").innerHTML = recent.length
            ? recent.map((line) => "<li>" + line + "</li>").join("")
            : "<li>No transactions available.</li>";

        notify("Dashboard loaded.", "success");
    } catch (err) {
        notify("Dashboard failed: " + err.message, "error");
    }
}

function renderDwChart(depositTotal, withdrawTotal) {
    if (typeof Chart === "undefined") {
        return;
    }
    const ctx = document.getElementById("dw-chart");
    if (dwChart) {
        dwChart.destroy();
    }
    dwChart = new Chart(ctx, {
        type: "bar",
        data: {
            labels: ["Deposits", "Withdrawals"],
            datasets: [{
                data: [depositTotal, withdrawTotal],
                backgroundColor: ["#228e6a", "#b05f29"]
            }]
        },
        options: { responsive: true, plugins: { legend: { display: false } } }
    });
}

function renderMonthlyChart(monthlySpending) {
    if (typeof Chart === "undefined") {
        return;
    }
    const labels = Object.keys(monthlySpending);
    const values = labels.map((k) => Number(monthlySpending[k]));
    const ctx = document.getElementById("monthly-chart");
    if (monthlyChart) {
        monthlyChart.destroy();
    }
    monthlyChart = new Chart(ctx, {
        type: "line",
        data: {
            labels,
            datasets: [{
                label: "Monthly Spending",
                data: values,
                fill: false,
                borderColor: "#16634f",
                tension: 0.25
            }]
        },
        options: { responsive: true }
    });
}

function openLock() {
    document.getElementById("lock-result").textContent = "";
    document.getElementById("lock-pin").value = "";
    document.getElementById("lock-overlay").classList.remove("hidden");
}

function closeLock() {
    document.getElementById("lock-overlay").classList.add("hidden");
}

async function unlockApp() {
    if (!requireAuth()) {
        return;
    }
    const pin = document.getElementById("lock-pin").value.trim();
    if (!/^\d{4,6}$/.test(pin)) {
        document.getElementById("lock-result").style.color = "#9f2020";
        document.getElementById("lock-result").textContent = "PIN must be 4 to 6 digits";
        return;
    }
    try {
        await apiRequest("/auth/verify-pin", "POST", { pin });
        pinUnlocked = true;
        localStorage.setItem("nb_pin_unlocked", "1");
        closeLock();
        notify("PIN verified successfully.", "success");
    } catch (err) {
        document.getElementById("lock-result").style.color = "#9f2020";
        document.getElementById("lock-result").textContent = err.message;
    }
}

updateAuthStatus();

async function bootstrapSession() {
    if (!authToken) {
        showSection("auth");
        return;
    }

    try {
        await apiRequest("/auth/session", "GET");
        showSection("dashboard");
    } catch (_err) {
        clearLocalSession();
        updateAuthStatus();
        showSection("auth");
    }
}

bootstrapSession();














