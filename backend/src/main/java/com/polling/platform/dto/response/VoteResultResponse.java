package com.polling.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteResultResponse {

    private UUID pollId;
    private Map<String, Long> voteCounts;
    private long totalVotes;

    public VoteResultResponse(UUID pollId, Map<String, Long> voteCounts) {
        this.pollId = pollId;
        this.voteCounts = voteCounts;
        this.totalVotes = voteCounts.values().stream().mapToLong(Long::longValue).sum();
    }
}
