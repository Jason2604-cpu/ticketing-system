package com.konohahelpdesk.dto.asset;

import com.konohahelpdesk.model.asset.Asset;
import com.konohahelpdesk.model.asset.AssetType;

/**
 * The light shape of an asset, for embedding in a ticket or a contract response.
 *
 * Exists so those payloads don't drag a whole AssetResponse — with its company, owner and warranty
 * arithmetic — along for what the UI renders as one line of text.
 */
public record AssetSummary(
    Long id,
    String assetTag,
    String name,
    AssetType type,
    String typeLabel,
    String serialNumber
) {
    public static AssetSummary of(Asset a) {
        if (a == null) return null;
        return new AssetSummary(
            a.getId(),
            a.getAssetTag(),
            a.getName(),
            a.getType(),
            a.getType() == null ? null : a.getType().getLabel(),
            a.getSerialNumber());
    }
}
