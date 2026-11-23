package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ShoppingCartMapper {
    @Update("update shopping_cart set number = number + 1 where user_id = #{userId} and dish_id <=> #{dishId} and setmeal_id <=> #{setmealId} and dish_flavor <=> #{dishFlavor}")
    int increment(ShoppingCart item);
    @Update("update shopping_cart set number = number - 1 where user_id = #{userId} and dish_id <=> #{dishId} and setmeal_id <=> #{setmealId} and dish_flavor <=> #{dishFlavor} and number > 1")
    int decrement(ShoppingCart item);
    @Delete("delete from shopping_cart where user_id = #{userId} and dish_id <=> #{dishId} and setmeal_id <=> #{setmealId} and dish_flavor <=> #{dishFlavor} and number = 1")
    int deleteOne(ShoppingCart item);
    @Insert("insert into shopping_cart (name, user_id, dish_id, setmeal_id, dish_flavor, number, amount, image, create_time) values (#{name}, #{userId}, #{dishId}, #{setmealId}, #{dishFlavor}, #{number}, #{amount}, #{image}, #{createTime})")
    void insert(ShoppingCart item);
    @Select("select * from shopping_cart where user_id = #{userId} order by create_time desc, id desc")
    List<ShoppingCart> list(Long userId);
    @Delete("delete from shopping_cart where user_id = #{userId}")
    int clean(Long userId);
}
