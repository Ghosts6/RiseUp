package com.riseup.gui.api;

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
    private static final MediaType JSON = MediaType.get("application/json");
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final String base;
    private final String token;

    public ApiClient(String server, String token) {
        String s = server.endsWith("/") ? server.substring(0, server.length() - 1) : server;
        this.base = s;
        this.token = token;
    }

    public JsonNode get(String path) {
        return exchange("GET", path, null);
    }

    public JsonNode post(String path, Object body) {
        return exchange("POST", path, body);
    }

    public JsonNode patch(String path, Object body) {
        return exchange("PATCH", path, body);
    }

    public void delete(String path) {
        exchange("DELETE", path, null);
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public String base() {
        return base;
    }

    public String token() {
        return token;
    }

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
                rb = RequestBody.create(new byte[0], JSON);
            }
            b.method(method, rb);
            try (Response resp = http.newCall(b.build()).execute()) {
                String text = resp.body() != null ? resp.body().string() : "";
                if (!resp.isSuccessful()) {
                    throw toApiException(resp.code(), text);
                }
                if (text.isBlank()) {
                    return mapper.nullNode();
                }
                return mapper.readTree(text);
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException("Cannot reach RiseUp server at " + base, 0, "network", 
                    "Can't reach the RiseUp server. Is it running?", e);
        }
    }

    private ApiException toApiException(int status, String text) {
        String detail = text;
        String code = null;
        try {
            if (text != null && text.trim().startsWith("{")) {
                JsonNode n = mapper.readTree(text);
                if (n.hasNonNull("detail")) {
                    detail = n.get("detail").asText();
                } else if (n.hasNonNull("message")) {
                    detail = n.get("message").asText();
                }
                if (n.hasNonNull("code")) {
                    code = n.get("code").asText();
                }
            }
        } catch (Exception ignored) {
            // keep raw text
        }
        if (detail == null || detail.isBlank()) {
            detail = "Request failed (HTTP " + status + ").";
        }
        // Prefer human detail as exception message so existing getMessage() call sites improve too
        return new ApiException(detail, status, code, detail, null);
    }
}
