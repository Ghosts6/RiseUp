package com.riseup.cli.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final String base;
    private final String token;

    public ApiClient(String server, String token) {
        String s = server == null ? "http://localhost:8000" : server.trim();
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        this.base = s;
        this.token = token == null || token.isBlank() ? "dev-token" : token;
    }

    public ObjectMapper mapper() { return mapper; }
    public JsonNode get(String path) { return exchange("GET", path, null); }
    public JsonNode post(String path, Object body) { return exchange("POST", path, body); }
    public JsonNode patch(String path, Object body) { return exchange("PATCH", path, body); }
    public void delete(String path) { exchange("DELETE", path, null); }

    private JsonNode exchange(String method, String path, Object body) {
        try {
            Request.Builder b = new Request.Builder()
                    .url(base + path)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json");
            RequestBody rb = null;
            if (body != null) {
                rb = RequestBody.create(mapper.writeValueAsBytes(body), JSON);
            } else if ("POST".equals(method) || "PATCH".equals(method)) {
                rb = RequestBody.create("{}", JSON);
            }
            b.method(method, rb);
            try (Response resp = http.newCall(b.build()).execute()) {
                String text = resp.body() != null ? resp.body().string() : "";
                if (!resp.isSuccessful()) {
                    throw new ApiException(resp.code(), "HTTP " + resp.code() + ": " + text);
                }
                if (text.isBlank()) return mapper.nullNode();
                return mapper.readTree(text);
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(0, "Cannot reach RiseUp server at " + base + " — " + e.getMessage());
        }
    }
}
