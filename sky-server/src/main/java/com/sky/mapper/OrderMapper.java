package com.sky.mapper;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    @Select("select id, number, user_id, status, pay_status, pay_method, amount from orders where number = #{number} and user_id = #{userId}")
    Orders getForPayment(@Param("number") String number, @Param("userId") Long userId);
    @Insert("insert into orders (number,status,user_id,address_book_id,order_time,pay_method,pay_status,amount,remark,phone,address,consignee,estimated_delivery_time,delivery_status,pack_amount,tableware_number,tableware_status) values (#{number},#{status},#{userId},#{addressBookId},#{orderTime},#{payMethod},#{payStatus},#{amount},#{remark},#{phone},#{address},#{consignee},#{estimatedDeliveryTime},#{deliveryStatus},#{packAmount},#{tablewareNumber},#{tablewareStatus})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Orders order);

    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);

    int update(Orders order);
    @Update("update orders set status = #{cancelled}, cancel_time = #{now}, cancel_reason = #{reason} where status = #{pending} and pay_status = #{unpaid} and order_time <= #{deadline}")
    int cancelExpired(@Param("cancelled") int cancelled, @Param("now") LocalDateTime now, @Param("reason") String reason,
                      @Param("pending") int pending, @Param("unpaid") int unpaid, @Param("deadline") LocalDateTime deadline);

    @Update("update orders set status = #{completed} where status = #{delivering} and order_time <= #{deadline}")
    int completeDelivering(@Param("completed") int completed, @Param("delivering") int delivering,
                           @Param("deadline") LocalDateTime deadline);
}
