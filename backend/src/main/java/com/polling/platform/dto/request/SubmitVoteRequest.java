package com.polling.platform.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SubmitVoteRequest {

    @NotNull(message = "Poll ID is required")
    private UUID pollId;

    @NotNull(message = "Option ID is required")
    private UUID optionId;
}
