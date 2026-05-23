package com.anvistudio.boutique.service;

import com.anvistudio.boutique.model.User;
import com.anvistudio.boutique.model.VerificationToken;
import com.anvistudio.boutique.model.VerificationToken.TokenType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service for sending live emails using the configured SMTP server.
 */
@Service
public class EmailService {

    private final JavaMailSender javaMailSender;

    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    /**
     * Sends the OTP to the user's email address.
     * MODIFIED: Content changes based on TokenType.
     * @param user The user object containing the target email in the username field.
     * @param token The token object containing the 6-digit OTP and type.
     */
    public void sendOtpEmail(User user, VerificationToken token) {

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        String subject;
        String action;

        // Customize subject and action based on the purpose of the OTP
        if (token.getTokenType() == TokenType.PASSWORD_RESET) {
            subject = "Anvi Studio: Password Reset Code (OTP)";
            action = "reset your password";
        } else if (token.getTokenType() == TokenType.NEW_EMAIL_VERIFICATION) { // NEW TYPE
            subject = "Anvi Studio: Verify Your New Email Address (OTP)";
            action = "change your login email to " + user.getUsername();
        } else { // REGISTRATION
            subject = "Anvi Studio: Your One-Time Password (OTP) for Registration";
            action = "activate your account";
        }

        mailMessage.setFrom("Anvi Studio Support <bharath161099@gmail.com>");
        // CRITICAL: Set the recipient to the User's username (which is the target email)
        mailMessage.setTo(user.getUsername());
        mailMessage.setSubject(subject);

        String emailContent = String.format(
                "Dear Customer,\n\n" +
                        "Your One-Time Password (OTP) to %s is:\n\n" +
                        "--- %s ---\n\n" +
                        "This OTP expires in %d minutes.\n\n" +
                        "If you did not request this, please ignore this email.",
                action, token.getToken(), 5);

        mailMessage.setText(emailContent);

        try {
            javaMailSender.send(mailMessage);
            System.out.println("SMTP: Successfully sent OTP email for " + token.getTokenType() + " to " + user.getUsername());
        } catch (Exception e) {
            System.err.println("SMTP ERROR: Failed to send OTP email for " + token.getTokenType() + " to " + user.getUsername());
            e.printStackTrace();
        }
    }
}