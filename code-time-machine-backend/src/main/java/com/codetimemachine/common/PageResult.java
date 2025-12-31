package com.codetimemachine.common;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    
    private List<T> list;
    private long total;
    private int page;
    private int pageSize;
    
    public static <T> PageResult<T> of(List<T> list, long total, int page, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        return result;
    }
}
