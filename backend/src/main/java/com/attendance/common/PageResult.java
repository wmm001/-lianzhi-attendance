package com.attendance.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private Long total;
    private List<T> list;
    private Long current;
    private Long size;
    private Long pages;

    public static <T> PageResult<T> of(Long total, List<T> list, Long current, Long size) {
        Long pages = (total + size - 1) / size;
        return new PageResult<>(total, list, current, size, pages);
    }
}