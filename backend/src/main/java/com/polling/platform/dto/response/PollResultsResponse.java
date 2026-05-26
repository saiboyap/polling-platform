package com.polling.platform.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PollResultsResponse {

    private UUID pollId;
    private String question;
    private String pollType;
    private String status;
    private int maxChoices;
    private long totalResponses;

    /** Non-null for SINGLE_CHOICE and MULTI_CHOICE polls. */
    private List<OptionResultResponse> optionResults;

    /** Non-null for FREE_TEXT polls. */
    private List<String> freeTextResponses;
}
