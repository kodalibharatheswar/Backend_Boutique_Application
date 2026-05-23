package com.anvistudio.boutique.service;

import io.agora.media.RtcTokenBuilder2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgoraTokenService {

    @Value("${agora.app.id}")
    private String appId;

    @Value("${agora.app.certificate}")
    private String appCertificate;

    // Token expiration time in seconds (e.g., 3600 = 1 hour)
    private final int tokenExpirationInSeconds = 3600;
    
    // Privilege expiration time in seconds
    private final int privilegeExpirationInSeconds = 3600;

    /**
     * Generates an RTC token for a given channel and user role.
     * 
     * @param channelName The name of the video room/channel.
     * @param uid The user ID (0 for an auto-assigned ID).
     * @param roleInt The role: 1 for Publisher (Admin), 2 for Subscriber (Customer).
     * @return The encrypted access token.
     */
    public String generateRtcToken(String channelName, int uid, int roleInt) {
        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
        
        RtcTokenBuilder2.Role role = (roleInt == 1) 
                ? RtcTokenBuilder2.Role.ROLE_PUBLISHER 
                : RtcTokenBuilder2.Role.ROLE_SUBSCRIBER;

        return tokenBuilder.buildTokenWithUid(
                appId, 
                appCertificate, 
                channelName, 
                uid, 
                role, 
                tokenExpirationInSeconds, 
                privilegeExpirationInSeconds
        );
    }
    
    public String getAppId() {
        return appId;
    }
}
