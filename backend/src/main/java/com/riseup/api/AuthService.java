package com.riseup.api;

import com.riseup.config.RiseUpProperties;
import com.riseup.domain.UserEntity;
import com.riseup.error.DomainException;
import com.riseup.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final RiseUpProperties properties;

    public AuthService(UserRepository users, RiseUpProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    public UserEntity resolveUser(String authorizationHeader, String tokenParam) {
        String token = tokenParam;
        if ((token == null || token.isBlank()) && authorizationHeader != null) {
            String h = authorizationHeader.strip();
            if (h.toLowerCase().startsWith("bearer ")) token = h.substring(7).strip();
            else token = h;
        }
        if (token == null || token.isBlank()) {
            if (!properties.isRequireAuth()) {
                return users.findByApiToken(properties.getDemoToken())
                        .or(() -> users.findFirstByOrderByIdAsc())
                        .orElseThrow(() -> new DomainException(401, "unauthorized", "No user"));
            }
            throw new DomainException(401, "unauthorized", "Missing token");
        }
        return users.findByApiToken(token)
                .orElseThrow(() -> new DomainException(401, "unauthorized", "Invalid token"));
    }

    public Integer resolveUserId(String authorizationHeader, String tokenParam) {
        return resolveUser(authorizationHeader, tokenParam).getId();
    }
}
