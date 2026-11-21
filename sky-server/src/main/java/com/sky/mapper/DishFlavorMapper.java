package com.sky.mapper;

import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface DishFlavorMapper {
    void insertBatch(@Param("flavors") List<DishFlavor> flavors);
    int deleteByDishIds(@Param("ids") List<Long> ids);
    @Select("select id, dish_id, name, value from dish_flavor where dish_id = #{dishId} order by id")
    List<DishFlavor> getByDishId(Long dishId);
}
