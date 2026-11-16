package com.konohahelpdesk.service.media;

import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.dto.company.CompanyResponse;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.media.MediaFile;
import com.konohahelpdesk.model.media.MediaKind;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Links uploaded images to their owner (user photo, company logo), replacing any previous one. */
@Service
public class ProfileMediaService {

    private final MediaStorageService storage;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanySubscriptionService subscriptions;

    public ProfileMediaService(MediaStorageService storage, UserRepository userRepository,
                               CompanyRepository companyRepository, CompanySubscriptionService subscriptions) {
        this.storage = storage;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public UserResponse setUserPhoto(Long userId, MultipartFile file, Long actorId) {
        User user = loadUser(userId);
        String previous = user.getPhotoId();
        // Staff accounts aren't attached to a customer company, so nothing to charge the upload to.
        if (user.getCompany() != null) {
            assertRoom(user.getCompany().getId(), previous, file.getSize());
        }
        MediaFile stored = storage.storeImage(file, MediaKind.USER_PHOTO, userId, actorId);
        user.setPhotoId(stored.getId());
        User saved = userRepository.save(user);
        storage.delete(previous);
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse removeUserPhoto(Long userId) {
        User user = loadUser(userId);
        String previous = user.getPhotoId();
        user.setPhotoId(null);
        User saved = userRepository.save(user);
        storage.delete(previous);
        return UserResponse.from(saved);
    }

    @Transactional
    public CompanyResponse setCompanyLogo(Long companyId, MultipartFile file, Long actorId) {
        Company company = loadCompany(companyId);
        String previous = company.getLogoId();
        assertRoom(companyId, previous, file.getSize());
        MediaFile stored = storage.storeImage(file, MediaKind.COMPANY_LOGO, companyId, actorId);
        company.setLogoId(stored.getId());
        Company saved = companyRepository.save(company);
        storage.delete(previous);
        return CompanyResponse.from(saved);
    }

    @Transactional
    public CompanyResponse removeCompanyLogo(Long companyId) {
        Company company = loadCompany(companyId);
        String previous = company.getLogoId();
        company.setLogoId(null);
        Company saved = companyRepository.save(company);
        storage.delete(previous);
        return CompanyResponse.from(saved);
    }

    /**
     * Storage quota for an image that replaces an existing one: the file being replaced is about to
     * be deleted, so it doesn't count against the limit — otherwise swapping a photo for one of the
     * same size could fail on a company sitting exactly at its quota.
     */
    private void assertRoom(Long companyId, String replacedMediaId, long incomingBytes) {
        long used = storage.usedBytesForCompany(companyId);
        if (replacedMediaId != null) {
            used -= storage.find(replacedMediaId).map(MediaFile::getSizeBytes).orElse(0L);
        }
        subscriptions.assertStorageAvailable(companyId, Math.max(0, used), incomingBytes);
    }

    private User loadUser(Long id) {
        return userRepository.findByIdAndStatusNot(id, UserStatus.IS_DELETED)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private Company loadCompany(Long id) {
        return companyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + id));
    }
}
