package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.service.AgoraTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
public class VideoRestController {

    private final AgoraTokenService agoraTokenService;

    public VideoRestController(AgoraTokenService agoraTokenService) {
        this.agoraTokenService = agoraTokenService;
    }

    /**
     * GET /api/video/token
     * Generates a temporary Agora token so the frontend can join the live broadcast
     * securely.
     */
    @GetMapping("/token")
    public ResponseEntity<?> getVideoToken(
            @RequestParam(defaultValue = "anvi_studio_live") String channelName,
            @RequestParam(defaultValue = "2") int role, // Default to 2 (Audience/Subscriber)
            @RequestParam(defaultValue = "0") int uid) {

        try {
            // Generate the secure token using the backend App Certificate
            String token = agoraTokenService.generateRtcToken(channelName, uid, role);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", token);
            response.put("channel", channelName);
            response.put("appId", agoraTokenService.getAppId()); // Frontend needs App ID to initialize
            response.put("uid", uid);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to generate video token: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
