package com.konohahelpdesk.util.validation;

public class InputValidator {
    
    private InputValidator() {}

    public static boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        return email != null && email.matches(emailRegex);
    }

    public static boolean isValidPhoneNumber(String phoneNumber) {
        String phoneRegex = "^\\+?[0-9]{10,15}$";
        return phoneNumber != null && phoneNumber.matches(phoneRegex);
    }

    public static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    
}
