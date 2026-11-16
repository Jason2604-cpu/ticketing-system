package com.konohahelpdesk.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A CUSTOMER_ADMIN inviting someone into their own company. Role is limited to customer roles. */
public class InviteTeamMemberRequest {

    @NotBlank @Size(max = 50) @Pattern(regexp = "^[\\p{L} .'-]+$", message = "First name contains invalid characters")
    private String firstName;

    @NotBlank @Size(max = 50) @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Last name contains invalid characters")
    private String lastName;

    @NotBlank @Email @Size(max = 254)
    private String email;

    @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must be 10-15 digits, optional leading +")
    private String phone;

    /** true = CUSTOMER_ADMIN, false = CUSTOMER. */
    private boolean admin = false;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
}
