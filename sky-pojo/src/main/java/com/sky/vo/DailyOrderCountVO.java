package com.sky.vo;
import lombok.Data;
import java.time.LocalDate;
@Data
public class DailyOrderCountVO {
    private LocalDate date;
    private Long totalOrders;
    private Long validOrders;
}
