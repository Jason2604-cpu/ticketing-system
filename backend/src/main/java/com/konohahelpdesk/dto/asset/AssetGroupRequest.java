package com.konohahelpdesk.dto.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create/update payload for an asset group. Members are assigned from the asset form, not here. */
public class AssetGroupRequest {

    @NotNull(message = "Company is required")
    private Long companyId;

    @NotBlank(message = "Group name is required")
    @Size(max = 120)
    private String name;

    private String description;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
