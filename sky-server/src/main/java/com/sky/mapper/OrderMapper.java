package com.sky.mapper;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.*;
@Mapper
public interface OrderMapper {
    @Select("select id, number, user_id, status, pay_status, pay_method, amount from orders where number = #{number} and user_id = #{userId}")
    Orders getForPayment(@Param("number") String number, @Param("userId") Long userId);
    @Insert("insert into orders (number,status,user_id,address_book_id,order_time,pay_method,pay_status,amount,remark,phone,address,consignee,estimated_delivery_time,delivery_status,pack_amount,tableware_number,tableware_status) values (#{number},#{status},#{userId},#{addressBookId},#{orderTime},#{payMethod},#{payStatus},#{amount},#{remark},#{phone},#{address},#{consignee},#{estimatedDeliveryTime},#{deliveryStatus},#{packAmount},#{tablewareNumber},#{tablewareStatus})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Orders order);
}
