package com.konohahelpdesk.repository.media;

import com.konohahelpdesk.model.media.MediaFile;
import com.konohahelpdesk.model.media.MediaKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, String> {

    Optional<MediaFile> findFirstByKindAndOwnerIdOrderByCreatedAtDesc(MediaKind kind, Long ownerId);

    List<MediaFile> findByKindAndOwnerIdOrderByCreatedAtDesc(MediaKind kind, Long ownerId);

    /**
     * Total bytes stored against one company, for the subscription storage quota.
     *
     * MediaFile has no company column — owner_id means a different thing per kind — so each kind
     * is resolved back to its company here:
     *   TICKET_ATTACHMENT   → the ticket's company, COMPANY_LOGO → the company itself,
     *   USER_PHOTO          → the owning user's company,
     *   CONTRACT_ATTACHMENT → the contract's company.
     */
    @Query("""
        select coalesce(sum(m.sizeBytes), 0) from MediaFile m
        where (m.kind = com.konohahelpdesk.model.media.MediaKind.TICKET_ATTACHMENT
                 and m.ownerId in (select t.id from Ticket t where t.company.id = :companyId))
           or (m.kind = com.konohahelpdesk.model.media.MediaKind.COMPANY_LOGO
                 and m.ownerId = :companyId)
           or (m.kind = com.konohahelpdesk.model.media.MediaKind.USER_PHOTO
                 and m.ownerId in (select u.id from User u where u.company.id = :companyId))
           or (m.kind = com.konohahelpdesk.model.media.MediaKind.CONTRACT_ATTACHMENT
                 and m.ownerId in (select c.id from ServiceContract c where c.company.id = :companyId))
        """)
    long sumBytesForCompany(@Param("companyId") Long companyId);
}
