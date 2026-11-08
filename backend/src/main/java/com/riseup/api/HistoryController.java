package com.riseup.api;

import com.riseup.error.ValidationFailedException;
import com.riseup.history.HistoryService;
import com.riseup.time.TimeUtil;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class HistoryController {
    private final HistoryService history;
    private final AuthService auth;

    public HistoryController(HistoryService history, AuthService auth) {
        this.history = history;
        this.auth = auth;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int page_size) {
        Integer sinceDays = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceDays = TimeUtil.parseRange(since);
            } catch (ValidationFailedException e) {
                throw new ValidationFailedException(e.getMessage(), "since");
            }
        }
        return history.list(auth.resolveUserId(authorization, token), type, sinceDays, page, page_size);
    }

    @GetMapping("/{kind}/{refId}")
    public Map<String, Object> explain(
            @PathVariable String kind,
            @PathVariable int refId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String token) {
        return history.explain(auth.resolveUserId(authorization, token), kind, refId);
    }
}
