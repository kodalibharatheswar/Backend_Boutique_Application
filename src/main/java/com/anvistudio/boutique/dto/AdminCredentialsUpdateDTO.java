package com.anvistudio.boutique.dto;

public class AdminCredentialsUpdateDTO {

    private String newUsername;
    private String newPassword;
    private String recoveryPhoneNumber;

    public AdminCredentialsUpdateDTO() {
    }

    public AdminCredentialsUpdateDTO(String newUsername, String newPassword, String recoveryPhoneNumber) {
        this.newUsername = newUsername;
        this.newPassword = newPassword;
        this.recoveryPhoneNumber = recoveryPhoneNumber;
    }

    public String getNewUsername() {
        return newUsername;
    }

    public void setNewUsername(String newUsername) {
        this.newUsername = newUsername;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getRecoveryPhoneNumber() {
        return recoveryPhoneNumber;
    }

    public void setRecoveryPhoneNumber(String recoveryPhoneNumber) {
        this.recoveryPhoneNumber = recoveryPhoneNumber;
    }
}
