package com.sky.task;
import com.sky.service.OrderTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@Slf4j
public class OrderTask {
    private final OrderTaskService service;
    public OrderTask(OrderTaskService service) { this.service = service; }
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeoutOrder() {
        int count = service.cancelExpiredOrders();
        log.info("超时未支付订单自动取消数量：{}", count);
    }
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDeliveryOrder() {
        int count = service.completeDeliveringOrders();
        log.info("派送中订单自动完成数量：{}", count);
    }}
