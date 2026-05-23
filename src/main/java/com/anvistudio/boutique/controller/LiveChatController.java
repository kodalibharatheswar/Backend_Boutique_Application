package com.anvistudio.boutique.controller;

import com.anvistudio.boutique.config.LiveViewerEventListener;
import com.anvistudio.boutique.dto.ChatMessageDto;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class LiveChatController {

    private final LiveViewerEventListener viewerEventListener;

    public LiveChatController(LiveViewerEventListener viewerEventListener) {
        this.viewerEventListener = viewerEventListener;
    }

    @MessageMapping("/live.chat.send")
    @SendTo("/topic/live.chat")
    public ChatMessageDto broadcastMessage(@Payload ChatMessageDto chatMessage) {
        // Add server-side timestamp
        chatMessage.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        return chatMessage;
    }

    @MessageMapping("/live.join")
    @SendTo("/topic/live.viewers")
    public int handleJoin(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        viewerEventListener.addViewer(sessionId);
        return viewerEventListener.getViewerCount();
    }

    @MessageMapping("/live.leave")
    @SendTo("/topic/live.viewers")
    public int handleLeave(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        viewerEventListener.removeViewerBySession(sessionId);
        return viewerEventListener.getViewerCount();
    }
}
