package com.konohahelpdesk.dto.company;

import com.konohahelpdesk.model.company.Company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Company details only. The subscription is changed via /api/companies/{id}/subscription. */
public class UpdateCompanyRequest {

    @NotBlank @Size(max = 150)
    private String name;

    @Size(max = 500)
    private String address;

    @Email @Size(max = 254)
    private String contactEmail;

    @Pattern(regexp = "^\\+?[0-9]{7,20}$", message = "Contact phone must be 7-20 digits, optionally prefixed with +")
    private String contactPhone;

    /** Engineer/admin notified on every new ticket for this company. Null clears it. */
    private Long mainEngineerId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public Long getMainEngineerId() { return mainEngineerId; }
    public void setMainEngineerId(Long mainEngineerId) { this.mainEngineerId = mainEngineerId; }
}
