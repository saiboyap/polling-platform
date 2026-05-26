package com.polling.platform.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollCreatedEvent {

    private String eventId;
    private String pollId;
    private String question;
    private String createdBy;
    private LocalDateTime createdAt;
}
