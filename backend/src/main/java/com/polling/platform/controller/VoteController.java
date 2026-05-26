package com.polling.platform.controller;

import com.polling.platform.dto.request.CastVoteRequest;
import com.polling.platform.dto.response.ApiResponse;
import com.polling.platform.dto.response.PollResultsResponse;
import com.polling.platform.service.VoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/votes")
@RequiredArgsConstructor
@Tag(name = "Votes", description = "Vote submission and results (also available under /api/polls/{id})")
public class VoteController {

    private final VoteService voteService;

    @PostMapping("/{pollId}")
    public ResponseEntity<ApiResponse<PollResultsResponse>> castVote(
            @PathVariable UUID pollId,
            @RequestBody CastVoteRequest request,
            Authentication authentication) {
        PollResultsResponse results = voteService.castVote(pollId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(results, "Vote submitted successfully"));
    }

    @GetMapping("/{pollId}/results")
    public ResponseEntity<ApiResponse<PollResultsResponse>> getResults(@PathVariable UUID pollId) {
        return ResponseEntity.ok(ApiResponse.success(
                voteService.getResults(pollId), "Results retrieved successfully"));
    }
}
