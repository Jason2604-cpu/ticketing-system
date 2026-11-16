package com.konohahelpdesk.repository.asset;

import com.konohahelpdesk.model.asset.Asset;
import com.konohahelpdesk.model.asset.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Filtering is done with Specifications rather than a wall of derived queries, for the same reason
 * {@code TicketService} does: half the filters are optional, and {@code :param is null} in JPQL
 * hits Postgres's untyped-null problem. The derived methods below are the ones that are always
 * the same shape.
 */
@Repository
public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

    /**
     * Detail load. company + owner are fetch-joined because the response names both and
     * open-in-view is off — without this, reading one asset costs three queries.
     */
    @Query("""
        select a from Asset a
        left join fetch a.company
        left join fetch a.owner
        left join fetch a.group
        where a.id = :id
        """)
    Optional<Asset> findDetailById(@Param("id") Long id);

    List<Asset> findByCompanyIdOrderByNameAsc(Long companyId);

    /** Used when a group is deleted: its assets stay, they just stop belonging to it. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Asset a set a.group = null where a.group.id = :groupId")
    int clearGroup(@Param("groupId") Long groupId);

    long countByGroupId(Long groupId);

    /** The assets a contract lists individually (not via groups). */
    @Query("select a from ServiceContract c join c.assets a where c.id = :contractId order by a.name asc")
    List<Asset> findCoveredByContract(@Param("contractId") Long contractId);

    long countByCompanyId(Long companyId);

    /** Tag uniqueness is per company, not global — see {@code Asset.assetTag}. */
    boolean existsByCompanyIdAndAssetTagIgnoreCase(Long companyId, String assetTag);

    /** The update-path variant: the asset being edited doesn't collide with itself. */
    boolean existsByCompanyIdAndAssetTagIgnoreCaseAndIdNot(Long companyId, String assetTag, Long id);

    /** Drives a warning, not a rejection — two identical spare units can legitimately share a blank. */
    boolean existsByCompanyIdAndSerialNumberIgnoreCaseAndIdNot(Long companyId, String serialNumber, Long id);

    boolean existsByCompanyIdAndSerialNumberIgnoreCase(Long companyId, String serialNumber);

    /**
     * The company picker's counts, as ONE grouped query rather than a count per row.
     *
     * This matters: {@code CompanyService.list()} already resolves a subscription per company, the
     * UI polls every 15s, and stacking a per-company asset count on top of that is how a landing
     * page becomes the slowest screen in the app.
     *
     * Returns {@code (companyId, total, expiringSoon, expired)}. The warranty columns exclude
     * retired and disposed kit — nobody renews a scrapped box, and counting it would send you
     * chasing renewals that don't exist — while the total counts everything on the register.
     */
    @Query("""
        select a.company.id,
               count(a),
               sum(case when a.warrantyEnd is not null
                         and a.status not in :outOfService
                         and a.warrantyEnd >= :today
                         and a.warrantyEnd <= :soonCutoff then 1 else 0 end),
               sum(case when a.warrantyEnd is not null
                         and a.status not in :outOfService
                         and a.warrantyEnd < :today then 1 else 0 end)
        from Asset a
        where a.company.id in :companyIds
        group by a.company.id
        """)
    List<Object[]> countsByCompany(@Param("companyIds") Collection<Long> companyIds,
                                   @Param("today") LocalDate today,
                                   @Param("soonCutoff") LocalDate soonCutoff,
                                   @Param("outOfService") Collection<AssetStatus> outOfService);
}
