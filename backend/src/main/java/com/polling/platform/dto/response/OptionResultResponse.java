package com.polling.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionResultResponse {

    private UUID optionId;
    private String optionText;
    private long voteCount;
    private double percentage;
}
