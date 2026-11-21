package com.sky.mapper;

import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SetmealDishMapper {
    List<SetmealDish> getByDishIds(@Param("ids") List<Long> ids);
}
