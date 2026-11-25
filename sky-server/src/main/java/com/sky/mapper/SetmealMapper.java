package com.sky.mapper;

import com.sky.entity.Setmeal;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealOverViewVO;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SetmealMapper {
    @Select("select coalesce(sum(case when status = #{enabled} then 1 else 0 end), 0) as sold, coalesce(sum(case when status = #{disabled} then 1 else 0 end), 0) as discontinued from setmeal")
    SetmealOverViewVO getWorkspaceOverview(@Param("enabled") Integer enabled, @Param("disabled") Integer disabled);
    @Select({"<script>select id,name,image,price,status from setmeal where id in <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"})
    List<Setmeal> findOrderProducts(@org.apache.ibatis.annotations.Param("ids") List<Long> ids);
    @Select("select id, name, image, price from setmeal where id = #{id}")
    Setmeal getCartProduct(Long id);
    List<Setmeal> list(Setmeal setmeal);

    @Select("select sd.name, sd.copies, d.image, d.description from setmeal_dish sd left join dish d on sd.dish_id = d.id where sd.setmeal_id = #{setmealId}")
    List<DishItemVO> getDishItemBySetmealId(Long setmealId);

    /**
     * 根据分类id查询套餐的数量
     * @param id
     * @return
     */
    @Select("select count(id) from setmeal where category_id = #{categoryId}")
    Integer countByCategoryId(Long id);

}
