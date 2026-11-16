package com.konohahelpdesk.dto.asset;

import com.konohahelpdesk.model.asset.Asset;
import com.konohahelpdesk.model.asset.AssetGroup;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** An asset group with its members in their light {@link AssetSummary} form. */
public record AssetGroupResponse(
    Long id,
    Long companyId,
    String companyName,
    String name,
    String description,
    int assetCount,
    List<AssetSummary> assets,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static AssetGroupResponse of(AssetGroup g) {
        List<AssetSummary> members = g.getAssets().stream()
            .sorted(Comparator.comparing((Asset a) -> a.getName() == null ? "" : a.getName(), String.CASE_INSENSITIVE_ORDER))
            .map(AssetSummary::of)
            .toList();
        return new AssetGroupResponse(
            g.getId(),
            g.getCompany().getId(),
            g.getCompany().getName(),
            g.getName(),
            g.getDescription(),
            members.size(),
            members,
            g.getCreatedAt(),
            g.getUpdatedAt());
    }
}
