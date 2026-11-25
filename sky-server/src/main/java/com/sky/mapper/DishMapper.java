package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import com.sky.annotation.AutoFill;
import com.sky.enumeration.OperationType;
import com.sky.entity.Dish;
import com.sky.dto.DishPageQueryDTO;
import com.sky.vo.DishVO;
import com.sky.vo.DishOverViewVO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DishMapper {

    @Select("select coalesce(sum(case when status = #{enabled} then 1 else 0 end), 0) as sold, coalesce(sum(case when status = #{disabled} then 1 else 0 end), 0) as discontinued from dish")
    DishOverViewVO getWorkspaceOverview(@Param("enabled") Integer enabled, @Param("disabled") Integer disabled);
    @Select({"<script>select id,name,image,price,status from dish where id in <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"})
    List<Dish> findOrderProducts(@Param("ids") List<Long> ids);

    List<Dish> list(Dish dish);
    List<DishVO> pageQuery(DishPageQueryDTO query);
    @Select("select id, name, category_id, price, image, description, status, update_time from dish where id = #{id}")
    Dish getById(Long id);
    List<Dish> getByIds(@Param("ids") List<Long> ids);
    int deleteByIds(@Param("ids") List<Long> ids);

    @AutoFill(OperationType.UPDATE)
    int update(Dish dish);

    @AutoFill(OperationType.INSERT)
    @Insert("insert into dish(name, category_id, price, image, description, status, create_time, update_time, create_user, update_user) values(#{name}, #{categoryId}, #{price}, #{image}, #{description}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser})")
    // 将数据库生成的主键写回 dish.id，供关联的口味记录使用。
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Dish dish);

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

}
