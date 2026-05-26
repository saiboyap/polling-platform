package com.polling.platform.controller;

import com.polling.platform.dto.response.ApiResponse;
import com.polling.platform.dto.response.TrendingPollResponse;
import com.polling.platform.service.TrendingPollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
@Tag(name = "Polls")
public class TrendingController {

    private final TrendingPollService trendingPollService;

    @GetMapping("/trending")
    @Operation(
            summary = "Top trending polls",
            description = "Returns polls ranked by total votes using a Redis sorted set. " +
                          "Degrades gracefully to an empty list when Redis is unavailable."
    )
    public ResponseEntity<ApiResponse<List<TrendingPollResponse>>> getTrending(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                trendingPollService.getTopPolls(limit), "Trending polls retrieved"));
    }
}
