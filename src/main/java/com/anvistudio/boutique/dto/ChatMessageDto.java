package com.anvistudio.boutique.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private String senderName;
    private String message;
    private String type; // e.g. "CHAT", "JOIN", "LEAVE"
    private String timestamp;
}
