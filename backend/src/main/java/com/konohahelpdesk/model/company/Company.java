package com.konohahelpdesk.model.company;

import com.konohahelpdesk.model.media.MediaFile;
import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    /** MediaFile id of the company logo (null = none). */
    @Column(name = "logo_id", length = 36)
    private String logoId;

    /** The engineer/admin notified whenever a new ticket comes in for this company. Optional. */
    @ManyToOne
    @JoinColumn(name = "main_engineer_id")
    private User mainEngineer;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getLogoId() { return logoId; }
    public void setLogoId(String logoId) { this.logoId = logoId; }
    public User getMainEngineer() { return mainEngineer; }
    public void setMainEngineer(User mainEngineer) { this.mainEngineer = mainEngineer; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
