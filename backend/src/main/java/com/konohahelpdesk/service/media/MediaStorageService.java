package com.konohahelpdesk.service.media;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.media.MediaFile;
import com.konohahelpdesk.model.media.MediaKind;
import com.konohahelpdesk.repository.media.MediaFileRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores uploads under app.media.base-path/<kind>/<uuid>.<ext>.
 *
 * Security properties:
 *  - The path is built ONLY from a server-generated UUID and a type-derived extension —
 *    the client's filename can't influence where the file lands (no path traversal).
 *  - The content type is detected from the file's leading bytes (magic numbers), never
 *    trusted from the multipart header or the extension.
 *  - Size is capped by app.media.max-file-size-mb.
 *  - Files are served through an authenticated endpoint, not as static web resources,
 *    so the media folder can live anywhere on disk.
 */
@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    private static final Map<String, String> EXT_BY_TYPE = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp",
        "image/gif", "gif",
        "application/pdf", "pdf",
        "text/csv", "csv",
        "text/plain", "txt"
    );

    private final AppProperties.Media cfg;
    private final MediaFileRepository repository;
    private final AuditLogger audit;
    private final Path root;

    public MediaStorageService(AppProperties props, MediaFileRepository repository, AuditLogger audit) throws IOException {
        this.cfg = props.getMedia();
        this.repository = repository;
        this.audit = audit;
        this.root = Paths.get(cfg.getBasePath()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        for (MediaKind kind : MediaKind.values()) {
            Files.createDirectories(root.resolve(folderOf(kind)));
        }
        log.info("Media storage root: {}", root);
    }

    // ── write ────────────────────────────────────────────────────────────────

    /** Validates and stores an image. Caller is responsible for authorization and for linking the id. */
    @Transactional
    public MediaFile storeImage(MultipartFile file, MediaKind kind, Long ownerId, Long uploadedBy) {
        return store(file, kind, ownerId, uploadedBy, cfg.getMaxFileSizeMb(), cfg.getAllowedImageTypes(), false);
    }

    /** Ticket attachments: images, PDF, CSV/plain text, larger size cap. */
    @Transactional
    public MediaFile storeAttachment(MultipartFile file, Long ticketId, Long uploadedBy) {
        return store(file, MediaKind.TICKET_ATTACHMENT, ticketId, uploadedBy, cfg.getMaxAttachmentSizeMb(), cfg.getAllowedAttachmentTypes(), true);
    }

    /** Contract documents: same rules as ticket attachments (images, PDF, CSV/plain text). */
    @Transactional
    public MediaFile storeContractDocument(MultipartFile file, Long contractId, Long uploadedBy) {
        return store(file, MediaKind.CONTRACT_ATTACHMENT, contractId, uploadedBy, cfg.getMaxAttachmentSizeMb(), cfg.getAllowedAttachmentTypes(), true);
    }

    /** Knowledge Base images and files: same rules as ticket attachments, owner = article id. */
    @Transactional
    public MediaFile storeKbAttachment(MultipartFile file, Long articleId, Long uploadedBy) {
        return store(file, MediaKind.KB_ATTACHMENT, articleId, uploadedBy, cfg.getMaxAttachmentSizeMb(), cfg.getAllowedAttachmentTypes(), true);
    }

    public long countFor(MediaKind kind, Long ownerId) {
        return repository.countByKindAndOwnerId(kind, ownerId);
    }

    /**
     * Bytes currently stored against a company: its tickets' attachments, its logo, its users'
     * photos and its contracts' documents. Feeds the subscription storage quota check.
     * Knowledge Base files are the provider's own content and aren't counted.
     */
    public long usedBytesForCompany(Long companyId) {
        return companyId == null ? 0L : repository.sumBytesForCompany(companyId);
    }

    public List<MediaFile> listFor(MediaKind kind, Long ownerId) {
        return repository.findByKindAndOwnerIdOrderByCreatedAtDesc(kind, ownerId);
    }

    private MediaFile store(MultipartFile file, MediaKind kind, Long ownerId, Long uploadedBy, long maxMb, List<String> allowed, boolean allowText) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("No file uploaded");
        }
        long maxBytes = maxMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleException("File is too large (max " + maxMb + " MB)");
        }

        String detectedType;
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(512);
            detectedType = detectImageType(head);
            if (detectedType == null) detectedType = detectDocumentType(head, file.getOriginalFilename(), allowText);
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read the uploaded file");
        }
        if (detectedType == null || !allowed.contains(detectedType)) {
            throw new BusinessRuleException("Unsupported file type. Allowed: " + String.join(", ", allowed));
        }

        String id = UUID.randomUUID().toString();
        String storedName = id + "." + EXT_BY_TYPE.get(detectedType);
        Path target = root.resolve(folderOf(kind)).resolve(storedName).normalize();
        if (!target.startsWith(root)) { // belt and braces — can't happen with a UUID name
            throw new IllegalStateException("Resolved media path escaped the media root");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to write media file {}", target, e);
            throw new BusinessRuleException("Could not save the uploaded file");
        }

        // The bytes are on disk before the row commits. If the surrounding transaction rolls back
        // (a later check fails, the quota is hit...), remove them again instead of leaving a file
        // no row points at.
        afterRollback(() -> deleteQuietly(target));

        MediaFile media = new MediaFile();
        media.setId(id);
        media.setKind(kind);
        media.setOwnerId(ownerId);
        media.setStoredName(storedName);
        media.setOriginalName(safeOriginalName(file.getOriginalFilename()));
        media.setContentType(detectedType);
        media.setSizeBytes(file.getSize());
        media.setUploadedBy(uploadedBy);
        MediaFile saved = repository.save(media);

        audit.event("MEDIA_UPLOADED", "id=" + id + " kind=" + kind + " ownerId=" + ownerId + " bytes=" + file.getSize());
        return saved;
    }

    /** Removes the row and the file. Silently ignores an id that no longer exists. */
    @Transactional
    public void delete(String id) {
        if (id == null) return;
        repository.findById(id).ifPresent(media -> {
            repository.delete(media);
            // Only once the row is really gone: deleting the file first and then rolling back
            // left a row whose file no longer existed.
            Path file = pathOf(media);
            afterCommit(() -> deleteQuietly(file));
            audit.event("MEDIA_DELETED", "id=" + id + " kind=" + media.getKind());
        });
    }

    // ── read ─────────────────────────────────────────────────────────────────

    public MediaFile get(String id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    public Optional<MediaFile> find(String id) {
        return id == null ? Optional.empty() : repository.findById(id);
    }

    public Resource open(MediaFile media) {
        Path path = pathOf(media);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("File not found");
        }
        return new FileSystemResource(path);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete media file {}: {}", file.getFileName(), e.getMessage());
        }
    }

    /** Runs after the current transaction commits — or right away when there is none. */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    /** Runs only if the current transaction ends up rolled back. */
    private static void afterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) action.run();
            }
        });
    }

    private Path pathOf(MediaFile media) {
        return root.resolve(folderOf(media.getKind())).resolve(media.getStoredName()).normalize();
    }

    private static String folderOf(MediaKind kind) {
        return kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Sniff by magic bytes: JPEG, PNG, GIF, WebP. Anything else → null (rejected). */
    static String detectImageType(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) return "image/png";
        if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8' && (h[4] == '7' || h[4] == '9') && h[5] == 'a') return "image/gif";
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "image/webp";
        return null;
    }

    /** PDF by magic bytes; CSV/plain text only when the sample is clean text (no NUL / control bytes). */
    static String detectDocumentType(byte[] h, String originalName, boolean allowText) {
        if (h.length >= 4 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F') return "application/pdf";
        if (!allowText || h.length == 0) return null;
        for (byte b : h) {
            int c = b & 0xFF;
            if (c == 0 || (c < 0x20 && c != '\t' && c != '\n' && c != '\r')) return null;
        }
        String name = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        return name.endsWith(".csv") ? "text/csv" : "text/plain";
    }

    private static String safeOriginalName(String name) {
        if (name == null || name.isBlank()) return "upload";
        // Keep only the final path segment and strip control characters; display-only anyway.
        String base = Arrays.stream(name.replace('\\', '/').split("/")).reduce((a, b) -> b).orElse(name);
        String cleaned = base.replaceAll("[\\p{Cntrl}]", "").trim();
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : (cleaned.isEmpty() ? "upload" : cleaned);
    }
}
