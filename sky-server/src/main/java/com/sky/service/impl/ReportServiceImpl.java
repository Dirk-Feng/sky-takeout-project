package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.dto.GoodsSalesDTO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.exception.BaseException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.vo.DailyUserCountVO;
import com.sky.vo.DailyOrderCountVO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.UserReportVO;
import com.sky.service.ReportService;
import com.sky.vo.DailyTurnoverVO;
import com.sky.vo.TurnoverReportVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportServiceImpl implements ReportService {
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    public ReportServiceImpl(OrderMapper orderMapper, UserMapper userMapper) { this.orderMapper = orderMapper; this.userMapper = userMapper; }

    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        if (begin == null || end == null || begin.isAfter(end)) throw new BaseException("统计日期范围无效");
        List<DailyTurnoverVO> rows = orderMapper.sumTurnoverByDate(begin.atStartOfDay(), end.plusDays(1).atStartOfDay(), Orders.COMPLETED);
        Map<LocalDate, BigDecimal> totals = new HashMap<>();
        for (DailyTurnoverVO row : rows) totals.put(row.getDate(), row.getTurnover());
        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dates.add(date);
            amounts.add(totals.getOrDefault(date, BigDecimal.ZERO));
        }
        return TurnoverReportVO.builder().dateList(StringUtils.join(dates, ","))
                .turnoverList(StringUtils.join(amounts, ",")).build();
    }
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        if (begin == null || end == null || begin.isAfter(end)) throw new BaseException("统计日期范围无效");
        List<DailyUserCountVO> rows = userMapper.countDailyUsers(begin.atStartOfDay(), end.plusDays(1).atStartOfDay());
        long total = userMapper.countUsersBefore(begin.atStartOfDay());
        Map<LocalDate, Long> dailyCounts = new HashMap<>();
        for (DailyUserCountVO row : rows) dailyCounts.put(row.getDate(), row.getNewUsers());
        List<LocalDate> dates = new ArrayList<>();
        List<Long> newUsers = new ArrayList<>();
        List<Long> totalUsers = new ArrayList<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            long daily = dailyCounts.getOrDefault(date, 0L);
            total += daily;
            dates.add(date);
            newUsers.add(daily);
            totalUsers.add(total);
        }
        return UserReportVO.builder().dateList(StringUtils.join(dates, ","))
                .newUserList(StringUtils.join(newUsers, ","))
                .totalUserList(StringUtils.join(totalUsers, ",")).build();
    }    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        if (begin == null || end == null || begin.isAfter(end)) throw new BaseException("统计日期范围无效");
        List<DailyOrderCountVO> rows = orderMapper.countOrdersByDate(begin.atStartOfDay(), end.plusDays(1).atStartOfDay(), Orders.COMPLETED);
        Map<LocalDate, DailyOrderCountVO> byDate = new HashMap<>();
        for (DailyOrderCountVO row : rows) byDate.put(row.getDate(), row);
        List<LocalDate> dates = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        List<Long> validCounts = new ArrayList<>();
        long total = 0, valid = 0;
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            DailyOrderCountVO row = byDate.get(date);
            long count = row == null ? 0 : row.getTotalOrders();
            long validCount = row == null ? 0 : row.getValidOrders();
            dates.add(date); counts.add(count); validCounts.add(validCount);
            total += count; valid += validCount;
        }
        if (total > Integer.MAX_VALUE || valid > Integer.MAX_VALUE) throw new BaseException("订单统计数量超出范围");
        return OrderReportVO.builder().dateList(StringUtils.join(dates, ","))
                .orderCountList(StringUtils.join(counts, ","))
                .validOrderCountList(StringUtils.join(validCounts, ","))
                .totalOrderCount((int) total).validOrderCount((int) valid)
                .orderCompletionRate(total == 0 ? 0.0 : (double) valid / total).build();
    }    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        if (begin == null || end == null || begin.isAfter(end)) throw new BaseException("统计日期范围无效");
        List<GoodsSalesDTO> rows = orderMapper.getSalesTop10(begin.atStartOfDay(), end.plusDays(1).atStartOfDay(), Orders.COMPLETED);
        List<String> names = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        for (GoodsSalesDTO row : rows) {
            names.add(row.getName());
            numbers.add(row.getNumber());
        }
        return SalesTop10ReportVO.builder().nameList(StringUtils.join(names, ","))
                .numberList(StringUtils.join(numbers, ",")).build();
    }}
