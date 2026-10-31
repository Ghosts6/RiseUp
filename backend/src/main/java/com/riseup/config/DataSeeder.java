package com.riseup.config;

import com.riseup.clock.Clock;
import com.riseup.domain.UserEntity;
import com.riseup.domain.UserPreferencesEntity;
import com.riseup.domain.UserProfileEntity;
import com.riseup.repository.UserPreferencesRepository;
import com.riseup.repository.UserProfileRepository;
import com.riseup.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final UserPreferencesRepository prefs;
    private final UserProfileRepository profiles;
    private final RiseUpProperties properties;
    private final Clock clock;

    public DataSeeder(
            UserRepository users,
            UserPreferencesRepository prefs,
            UserProfileRepository profiles,
            RiseUpProperties properties,
            Clock clock) {
        this.users = users;
        this.prefs = prefs;
        this.profiles = profiles;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UserEntity user =
                users.findByApiToken(properties.getDemoToken())
                        .or(() -> users.findFirstByOrderByIdAsc())
                        .orElse(null);
        if (user == null) {
            user = new UserEntity();
            user.setName("Demo User");
            user.setEmail("demo@riseup.local");
            user.setPhone("+15555550100");
            user.setTimezone("America/Toronto");
            user.setApiToken(properties.getDemoToken());
            user.setCreatedAt(clock.now());
            users.save(user);
        } else if (!properties.getDemoToken().equals(user.getApiToken())) {
            user.setApiToken(properties.getDemoToken());
            users.save(user);
        }
        if (!prefs.existsById(user.getId())) {
            UserPreferencesEntity p = new UserPreferencesEntity();
            p.setUserId(user.getId());
            prefs.save(p);
        }
        if (!profiles.existsById(user.getId())) {
            UserProfileEntity profile = new UserProfileEntity();
            profile.setUserId(user.getId());
            profile.setUpdatedAt(clock.now());
            profiles.save(profile);
        }
    }
}
