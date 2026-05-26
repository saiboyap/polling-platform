package com.polling.platform.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteSubmittedEvent {

    private String eventId;
    private String pollId;
    private String optionId;
    private String username;
    private LocalDateTime votedAt;
    private Map<String, Long> currentVoteCounts;
}
