package com.sky.vo;
import lombok.Data;
import java.time.LocalDate;
@Data
public class DailyUserCountVO {
    private LocalDate date;
    private Long newUsers;
}
