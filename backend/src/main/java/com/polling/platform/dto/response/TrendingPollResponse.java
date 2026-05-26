package com.polling.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendingPollResponse {

    private String pollId;
    private String question;
    private String createdBy;
    private String status;
    private String pollType;
    private long   totalVotes;
    private int    rank;
}
