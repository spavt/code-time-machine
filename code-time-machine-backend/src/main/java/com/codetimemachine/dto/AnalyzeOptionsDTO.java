package com.codetimemachine.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AnalyzeOptionsDTO {

    private Integer depth = 500;

    private LocalDateTime since;

    private LocalDateTime until;

    private List<String> pathFilters;

    private Boolean shallow = true;

    private Boolean singleBranch = true;

    public int getEffectiveCloneDepth() {
        if (depth == null || depth <= 0) {
            return shallow ? 10000 : 0;
        }
        return depth;
    }

    public int getEffectiveAnalyzeDepth() {
        if (depth == null || depth <= 0) {
            return Integer.MAX_VALUE;
        }
        return depth;
    }

    public static AnalyzeOptionsDTO fast() {
        AnalyzeOptionsDTO options = new AnalyzeOptionsDTO();
        options.setDepth(100);
        options.setShallow(true);
        return options;
    }

    public static AnalyzeOptionsDTO recommended() {
        AnalyzeOptionsDTO options = new AnalyzeOptionsDTO();
        options.setDepth(500);
        options.setShallow(true);
        return options;
    }

    public static AnalyzeOptionsDTO deep() {
        AnalyzeOptionsDTO options = new AnalyzeOptionsDTO();
        options.setDepth(2000);
        options.setShallow(true);
        return options;
    }

    public static AnalyzeOptionsDTO full() {
        AnalyzeOptionsDTO options = new AnalyzeOptionsDTO();
        options.setDepth(-1);
        options.setShallow(false);
        return options;
    }
}
