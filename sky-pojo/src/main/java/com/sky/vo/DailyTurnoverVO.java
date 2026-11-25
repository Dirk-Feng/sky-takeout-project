package com.sky.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DailyTurnoverVO {
    private LocalDate date;
    private BigDecimal turnover;
}
