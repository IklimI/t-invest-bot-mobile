package com.tinvestbot.mobile;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class TInvestClient {
    private static final String BASE = "https://invest-public-api.tbank.ru/rest/";
    private static final String GET_ACCOUNTS =
            "tinkoff.public.invest.api.contract.v1.UsersService/GetAccounts";
    private static final String GET_PORTFOLIO =
            "tinkoff.public.invest.api.contract.v1.OperationsService/GetPortfolio";

    private final String token;

    public TInvestClient(String token) {
        this.token = token;
    }

    public JSONObject getAccounts() throws Exception {
        JSONObject body = new JSONObject();
        body.put("status", "ACCOUNT_STATUS_OPEN");
        return post(GET_ACCOUNTS, body);
    }

    public JSONObject getPortfolio(String accountId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("accountId", accountId);
        body.put("currency", "RUB");
        return post(GET_PORTFOLIO, body);
    }

    private JSONObject post(String method, JSONObject body) throws Exception {
        if (!GET_ACCOUNTS.equals(method) && !GET_PORTFOLIO.equals(method)) {
            throw new IllegalArgumentException("Only read-only API methods are allowed");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE + method).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "TInvestBotMobile/0.1 Android");

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }

        int code = connection.getResponseCode();
        InputStream input = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String response = readAll(input);
        String trackingId = connection.getHeaderField("x-tracking-id");
        connection.disconnect();

        if (code < 200 || code >= 300) {
            String suffix = trackingId == null ? "" : "\ntrackingId: " + trackingId;
            throw new ApiException(code, friendlyError(code, response) + suffix);
        }
        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
    }

    private String friendlyError(int code, String raw) {
        if (code == 401) return "Токен отсутствует, просрочен или недействителен.";
        if (code == 403) return "Недостаточно прав токена для чтения этого счёта.";
        if (code == 429) return "Слишком много запросов к API. Повторите позже.";
        if (code >= 500) return "Сервис T-Invest временно недоступен (HTTP " + code + ").";
        if (raw != null && !raw.trim().isEmpty()) return "Ошибка API HTTP " + code + ": " + raw;
        return "Ошибка API HTTP " + code;
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    public static final class ApiException extends Exception {
        public final int httpCode;
        ApiException(int httpCode, String message) {
            super(message);
            this.httpCode = httpCode;
        }
    }
}
