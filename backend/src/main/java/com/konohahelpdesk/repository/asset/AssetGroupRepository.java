package com.konohahelpdesk.repository.asset;

import com.konohahelpdesk.model.asset.AssetGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Every read fetch-joins the members: open-in-view is off and every response lists them, so a
 * lazy collection would either throw or cost a query per group.
 */
@Repository
public interface AssetGroupRepository extends JpaRepository<AssetGroup, Long> {

    @Query("""
        select distinct g from AssetGroup g
        left join fetch g.company
        left join fetch g.assets
        where g.company.id = :companyId
        order by g.name asc
        """)
    List<AssetGroup> findForCompany(@Param("companyId") Long companyId);

    @Query("""
        select g from AssetGroup g
        left join fetch g.company
        left join fetch g.assets
        where g.id = :id
        """)
    Optional<AssetGroup> findDetailById(@Param("id") Long id);

    @Query("""
        select distinct g from AssetGroup g
        left join fetch g.company
        left join fetch g.assets
        where g.id in :ids
        """)
    List<AssetGroup> findAllWithAssets(@Param("ids") Collection<Long> ids);

    /** The groups a contract covers, members included. Used by the contract detail response. */
    @Query("""
        select distinct g from ServiceContract c
        join c.assetGroups g
        left join fetch g.assets
        where c.id = :contractId
        """)
    List<AssetGroup> findCoveredByContract(@Param("contractId") Long contractId);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);
}
