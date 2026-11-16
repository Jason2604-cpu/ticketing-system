package com.konohahelpdesk.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A user editing their own name / phone. Email and role are not self-service. */
public class UpdateProfileRequest {

    @NotBlank @Size(max = 50) @Pattern(regexp = "^[\\p{L} .'-]+$", message = "First name contains invalid characters")
    private String firstName;

    @NotBlank @Size(max = 50) @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Last name contains invalid characters")
    private String lastName;

    @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must be 10-15 digits, optional leading +")
    private String phone;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
