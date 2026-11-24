package com.sky.service.impl;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.service.OrderTaskService;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
@Service
public class OrderTaskServiceImpl implements OrderTaskService {
    private final OrderMapper mapper;
    public OrderTaskServiceImpl(OrderMapper mapper) { this.mapper = mapper; }
    public int cancelExpiredOrders() {
        LocalDateTime now = LocalDateTime.now();
        return mapper.cancelExpired(Orders.CANCELLED, now, "订单超时，自动取消", Orders.PENDING_PAYMENT, Orders.UN_PAID, now.minusMinutes(15));
    }
    public int completeDeliveringOrders() {
        // 课程规则按下单时间判断，并非实际派送时长；旧订单刚进入派送也可能被完成。
        // 后续可增加独立的派送开始时间，再以该时间判断。
        return mapper.completeDelivering(Orders.COMPLETED, Orders.DELIVERY_IN_PROGRESS, LocalDateTime.now().minusMinutes(60));
    }}
