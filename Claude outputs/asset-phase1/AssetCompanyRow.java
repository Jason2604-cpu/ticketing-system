package com.konohahelpdesk.dto.asset;

/**
 * One row of the asset-management company picker.
 *
 * The warranty counts are what turn a navigation step into a status board: the landing page
 * answers "who do I need to call about a renewal" before anyone clicks into a company.
 */
public record AssetCompanyRow(
    Long companyId,
    String companyName,
    String logoUrl,
    long assetCount,
    long expiringSoonCount,
    long expiredCount
) {}
