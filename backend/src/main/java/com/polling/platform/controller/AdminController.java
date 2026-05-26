package com.polling.platform.controller;

import com.polling.platform.dto.response.AdminStatsResponse;
import com.polling.platform.dto.response.ApiResponse;
import com.polling.platform.entity.PollStatus;
import com.polling.platform.repository.FreeTextVoteRepository;
import com.polling.platform.repository.PollRepository;
import com.polling.platform.repository.UserRepository;
import com.polling.platform.repository.VoteRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Platform management — requires ADMIN role")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final PollRepository           pollRepository;
    private final UserRepository           userRepository;
    private final VoteRepository           voteRepository;
    private final FreeTextVoteRepository   freeTextVoteRepository;

    @GetMapping("/stats")
    @Operation(summary = "Platform aggregate statistics")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> stats() {
        AdminStatsResponse stats = AdminStatsResponse.builder()
                .totalPolls(pollRepository.count())
                .activePolls(pollRepository.countByStatus(PollStatus.ACTIVE))
                .closedPolls(pollRepository.countByStatus(PollStatus.CLOSED))
                .expiredPolls(pollRepository.countByStatus(PollStatus.EXPIRED))
                .totalUsers(userRepository.count())
                .totalVotes(voteRepository.count())
                .totalFreeTextResponses(freeTextVoteRepository.count())
                .build();

        return ResponseEntity.ok(ApiResponse.success(stats, "Stats retrieved"));
    }
}
