package com.konohahelpdesk.model.media;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Metadata for a file on disk. The primary key is a random UUID and the on-disk
 * name is <uuid>.<ext> — the client-supplied filename is stored for display only
 * and never touches the filesystem path.
 */
@Entity
@Table(name = "media_files", indexes = {
        @Index(name = "ix_media_owner", columnList = "kind, owner_id")
})
public class MediaFile {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MediaKind kind;

    /** userId for USER_PHOTO, companyId for COMPANY_LOGO, ticketId for TICKET_ATTACHMENT. */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "stored_name", nullable = false, length = 50)
    private String storedName;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public MediaKind getKind() { return kind; }
    public void setKind(MediaKind kind) { this.kind = kind; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
