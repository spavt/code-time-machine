package com.codetimemachine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrefetchRequest {

    private String commitHash;

    private String filePath;
}
