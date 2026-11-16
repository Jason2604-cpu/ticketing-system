package com.konohahelpdesk.service.user;

import com.konohahelpdesk.dto.user.NotificationPreferences;
import com.konohahelpdesk.dto.user.UpdateProfileRequest;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Things a signed-in user may change about themselves. */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final AuditLogger audit;

    public ProfileService(UserRepository userRepository, AuditLogger audit) {
        this.userRepository = userRepository;
        this.audit = audit;
    }

    @Transactional
    public UserResponse updateProfile(User user, UpdateProfileRequest req) {
        user.setFirstName(req.getFirstName().trim());
        user.setLastName(req.getLastName().trim());
        user.setPhone(req.getPhone().trim());
        User saved = userRepository.save(user);
        audit.event("PROFILE_UPDATED", AuditLogger.describe(user));
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateNotifications(User user, NotificationPreferences prefs) {
        prefs.applyTo(user);
        return UserResponse.from(userRepository.save(user));
    }
}
