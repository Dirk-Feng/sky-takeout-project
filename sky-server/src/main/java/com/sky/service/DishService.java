package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;
import java.util.List;

public interface DishService {
    void save(DishDTO dto);
    DishVO getById(Long id);
    void update(DishDTO dto);
    void startOrStop(Integer status, Long id);
    void deleteBatch(List<Long> ids);
    PageResult<DishVO> pageQuery(DishPageQueryDTO query);
}
