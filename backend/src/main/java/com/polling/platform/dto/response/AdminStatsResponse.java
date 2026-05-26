package com.polling.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {

    private long totalPolls;
    private long activePolls;
    private long closedPolls;
    private long expiredPolls;
    private long totalUsers;
    private long totalVotes;
    private long totalFreeTextResponses;
}
