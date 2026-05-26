package com.polling.platform.controller;

import com.polling.platform.dto.request.CastVoteRequest;
import com.polling.platform.dto.request.CreatePollRequest;
import com.polling.platform.dto.response.ApiResponse;
import com.polling.platform.dto.response.PollResponse;
import com.polling.platform.dto.response.PollResultsResponse;
import com.polling.platform.service.PollService;
import com.polling.platform.service.VoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
@Tag(name = "Polls", description = "Create, browse, vote on, and manage polls")
public class PollController {

    private final PollService pollService;
    private final VoteService voteService;

    // ------------------------------------------------------------------
    // Poll CRUD
    // ------------------------------------------------------------------

    @PostMapping
    @Operation(summary = "Create a new poll", description = "Requires authentication. Supports SINGLE_CHOICE, MULTI_CHOICE, and FREE_TEXT poll types.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Poll created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated", content = @Content)
    })
    public ResponseEntity<ApiResponse<PollResponse>> createPoll(
            @Valid @RequestBody CreatePollRequest request,
            Authentication authentication) {
        PollResponse response = pollService.createPoll(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Poll created successfully"));
    }

    @GetMapping
    @Operation(summary = "List active polls (paginated, newest first)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Polls retrieved")
    })
    public ResponseEntity<ApiResponse<Page<PollResponse>>> getActivePolls(
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 50)")     @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(
                pollService.getActivePolls(pageable), "Polls retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single poll by ID")
    public ResponseEntity<ApiResponse<PollResponse>> getPollById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                pollService.getPollById(id), "Poll retrieved successfully"));
    }

    @PatchMapping("/{id}/close")
    @Operation(summary = "Close a poll (owner or admin only)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Poll closed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the owner", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Poll not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> closePoll(
            @PathVariable UUID id,
            Authentication authentication) {
        pollService.closePoll(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(null, "Poll closed successfully"));
    }

    // ------------------------------------------------------------------
    // Voting & results
    // ------------------------------------------------------------------

    @PostMapping("/{pollId}/vote")
    @Operation(summary = "Submit a vote",
               description = "Send `optionIds` for choice polls or `freeText` for FREE_TEXT polls. Each user may vote once.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Vote recorded — returns live results"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Already voted", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Poll closed or invalid request", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Vote rate limit exceeded", content = @Content)
    })
    public ResponseEntity<ApiResponse<PollResultsResponse>> castVote(
            @PathVariable UUID pollId,
            @RequestBody CastVoteRequest request,
            Authentication authentication) {
        PollResultsResponse results = voteService.castVote(pollId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(results, "Vote submitted successfully"));
    }

    @GetMapping("/{pollId}/results")
    @Operation(summary = "Get live results for a poll",
               description = "Sourced from Redis with automatic DB fallback. Public endpoint.")
    public ResponseEntity<ApiResponse<PollResultsResponse>> getResults(@PathVariable UUID pollId) {
        return ResponseEntity.ok(ApiResponse.success(
                voteService.getResults(pollId), "Results retrieved successfully"));
    }
}
