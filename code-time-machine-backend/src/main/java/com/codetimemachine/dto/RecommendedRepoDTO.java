package com.codetimemachine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedRepoDTO {

    private String name;
    private String url;
    private String description;
    private String level;
    private List<String> tags;
    private String stars;
    private String language;
}
