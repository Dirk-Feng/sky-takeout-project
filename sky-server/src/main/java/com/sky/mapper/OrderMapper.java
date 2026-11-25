package com.sky.mapper;
import com.sky.entity.Orders;
import com.sky.dto.GoodsSalesDTO;
import com.sky.vo.DailyOrderCountVO;
import com.sky.vo.DailyTurnoverVO;
import com.sky.vo.OrderOverViewVO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    @Select("select count(*) as allOrders, coalesce(sum(case when status = #{waiting} then 1 else 0 end), 0) as waitingOrders, coalesce(sum(case when status = #{delivered} then 1 else 0 end), 0) as deliveredOrders, coalesce(sum(case when status = #{completed} then 1 else 0 end), 0) as completedOrders, coalesce(sum(case when status = #{cancelled} then 1 else 0 end), 0) as cancelledOrders from orders where order_time > #{beginTime}")
    OrderOverViewVO getWorkspaceOrderOverview(@Param("beginTime") LocalDateTime beginTime,
            @Param("waiting") Integer waiting, @Param("delivered") Integer delivered,
            @Param("completed") Integer completed, @Param("cancelled") Integer cancelled);
    List<GoodsSalesDTO> getSalesTop10(@Param("beginTime") LocalDateTime beginTime,
                                      @Param("endTimeExclusive") LocalDateTime endTimeExclusive,
                                      @Param("completedStatus") Integer completedStatus);
    List<DailyOrderCountVO> countOrdersByDate(@Param("beginTime") LocalDateTime beginTime,
                                              @Param("endTimeExclusive") LocalDateTime endTimeExclusive,
                                              @Param("completedStatus") Integer completedStatus);
    List<DailyTurnoverVO> sumTurnoverByDate(@Param("beginTime") LocalDateTime beginTime,
                                            @Param("endTimeExclusive") LocalDateTime endTimeExclusive,
                                            @Param("status") Integer status);
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
