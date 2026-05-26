package com.polling.platform.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PollResponse {

    private UUID id;
    private String question;
    private String createdBy;
    private String status;
    private String pollType;
    private int maxChoices;
    private List<PollOptionResponse> options;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private long totalVotes;
}
