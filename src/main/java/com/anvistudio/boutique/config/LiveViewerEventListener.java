package com.anvistudio.boutique.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveViewerEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    // Track viewer sessions by session ID
    private final ConcurrentHashMap<String, String> viewerSessions = new ConcurrentHashMap<>();

    public LiveViewerEventListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void addViewer(String sessionId) {
        viewerSessions.put(sessionId, sessionId);
        broadcastViewerCount();
    }

    public void removeViewerBySession(String sessionId) {
        viewerSessions.remove(sessionId);
        broadcastViewerCount();
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        // If this disconnected session was a viewer, remove and rebroadcast
        if (viewerSessions.remove(sessionId) != null) {
            broadcastViewerCount();
        }
    }

    public int getViewerCount() {
        return viewerSessions.size();
    }

    private void broadcastViewerCount(int count) {
        messagingTemplate.convertAndSend("/topic/live.viewers", count);
    }

    private void broadcastViewerCount() {
        messagingTemplate.convertAndSend("/topic/live.viewers", viewerSessions.size());
    }
}
