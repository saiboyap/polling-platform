package com.polling.platform.dto.request;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CastVoteRequest {

    /** Option IDs for SINGLE_CHOICE (exactly 1) or MULTI_CHOICE (1..maxChoices). */
    private List<UUID> optionIds;

    /** Free-form response text for FREE_TEXT polls. */
    private String freeText;
}
