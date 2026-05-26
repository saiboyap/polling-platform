package com.polling.platform.dto.request;

import com.polling.platform.entity.PollType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreatePollRequest {

    @NotBlank(message = "Question is required")
    @Size(max = 1000, message = "Question must not exceed 1000 characters")
    private String question;

    @NotNull(message = "Poll type is required")
    private PollType pollType = PollType.SINGLE_CHOICE;

    /** Required for SINGLE_CHOICE and MULTI_CHOICE; ignored for FREE_TEXT. */
    @Size(max = 10, message = "Maximum 10 options allowed")
    private List<@NotBlank(message = "Option text must not be blank") String> options;

    /** Enforced only for MULTI_CHOICE polls. */
    @Min(value = 1, message = "Max choices must be at least 1")
    @Max(value = 10, message = "Max choices cannot exceed 10")
    private int maxChoices = 1;

    private LocalDateTime expiresAt;
}
