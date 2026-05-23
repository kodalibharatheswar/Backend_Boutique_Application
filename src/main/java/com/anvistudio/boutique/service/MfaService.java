package com.anvistudio.boutique.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.springframework.stereotype.Service;

@Service
public class MfaService {

    private final GoogleAuthenticator gAuth;

    public MfaService() {
        this.gAuth = new GoogleAuthenticator();
    }

    /**
     * Generates a new MFA secret key.
     */
    public String generateSecret() {
        final GoogleAuthenticatorKey key = gAuth.createCredentials();
        return key.getKey();
    }

    /**
     * Generates a Google Authenticator QR code URL.
     */
    public String getQrCodeUrl(String secret, String email) {
        return GoogleAuthenticatorQRGenerator.getOtpAuthURL("Anvi Boutique", email, new GoogleAuthenticatorKey.Builder(secret).build());
    }

    /**
     * Verifies the provided code against the secret key.
     */
    public boolean verifyCode(String secret, int code) {
        return gAuth.authorize(secret, code);
    }
}
