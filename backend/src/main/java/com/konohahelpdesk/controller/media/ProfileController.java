package com.konohahelpdesk.controller.media;

import com.konohahelpdesk.dto.user.NotificationPreferences;
import com.konohahelpdesk.dto.user.UpdateProfileRequest;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.media.ProfileMediaService;
import com.konohahelpdesk.service.user.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The signed-in user's own profile. Any ACTIVE role. */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileMediaService profileMedia;
    private final ProfileService profileService;
    private final CurrentUser currentUser;

    public ProfileController(ProfileMediaService profileMedia, ProfileService profileService, CurrentUser currentUser) {
        this.profileMedia = profileMedia;
        this.profileService = profileService;
        this.currentUser = currentUser;
    }

    /** Name and phone. Email/role changes go through an admin. */
    @PutMapping
    public ResponseEntity<UserResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.updateProfile(currentUser.get(), request));
    }

    @PutMapping("/notifications")
    public ResponseEntity<UserResponse> notifications(@Valid @RequestBody NotificationPreferences prefs) {
        return ResponseEntity.ok(profileService.updateNotifications(currentUser.get(), prefs));
    }

    /** multipart/form-data with a single part named "file". Replaces any existing photo. */
    @PutMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadPhoto(@RequestPart("file") MultipartFile file) {
        Long me = currentUser.get().getId();
        return ResponseEntity.ok(profileMedia.setUserPhoto(me, file, me));
    }

    @DeleteMapping("/photo")
    public ResponseEntity<UserResponse> deletePhoto() {
        return ResponseEntity.ok(profileMedia.removeUserPhoto(currentUser.get().getId()));
    }
}
