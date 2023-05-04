package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * @Description 滚动分页结果
 * @Date 2023-05-04-16-09
 * @Author qianzhikang
 */
@Data
public class ScoreResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
