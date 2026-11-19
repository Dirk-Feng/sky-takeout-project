package com.sky.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 封装分页查询结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    // 分页查询的总条数和当前页记录可在 Service 中封装，再作为 Result.data 返回。
    private long total; //总记录数

    private List<T> records; //当前页数据集合

}
