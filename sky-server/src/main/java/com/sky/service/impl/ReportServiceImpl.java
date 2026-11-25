package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.dto.GoodsSalesDTO;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import com.sky.exception.BaseException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportServiceImpl implements ReportService {
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final WorkspaceService workspaceService;

    public ReportServiceImpl(OrderMapper orderMapper, UserMapper userMapper, WorkspaceService workspaceService) { this.orderMapper = orderMapper; this.userMapper = userMapper;
        this.workspaceService = workspaceService;
    }

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
    }

    public void exportBusinessData(HttpServletResponse response) {
        LocalDate today = LocalDate.now();
        LocalDate begin = today.minusDays(30);
        LocalDate end = today.minusDays(1);
        BusinessDataVO totals = workspaceService.getBusinessData(begin.atStartOfDay(), today.atStartOfDay());
        InputStream resource = getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        if (resource == null) throw new IllegalStateException("运营数据报表模板不存在");
        try (InputStream in = resource; XSSFWorkbook excel = new XSSFWorkbook(in)) {
            Sheet sheet = excel.getSheet("Sheet1");
            if (sheet == null) throw new IllegalStateException("运营数据报表模板缺少Sheet1");
            sheet.getRow(1).getCell(1).setCellValue("时间：" + begin + "至" + end);
            Row row = sheet.getRow(3);
            row.getCell(2).setCellValue(number(totals.getTurnover()));
            row.getCell(4).setCellValue(number(totals.getOrderCompletionRate()));
            row.getCell(6).setCellValue(number(totals.getNewUsers()));
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(number(totals.getValidOrderCount()));
            row.getCell(4).setCellValue(number(totals.getUnitPrice()));
            for (int i = 0; i < 30; i++) {
                LocalDate date = begin.plusDays(i);
                BusinessDataVO daily = workspaceService.getBusinessData(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
                row = sheet.getRow(7 + i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(number(daily.getTurnover()));
                row.getCell(3).setCellValue(number(daily.getValidOrderCount()));
                row.getCell(4).setCellValue(number(daily.getOrderCompletionRate()));
                row.getCell(5).setCellValue(number(daily.getUnitPrice()));
                row.getCell(6).setCellValue(number(daily.getNewUsers()));
            }
            String filename = URLEncoder.encode("运营数据报表.xlsx", StandardCharsets.UTF_8.name()).replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"report.xlsx\"; filename*=UTF-8''" + filename);
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("导出运营数据报表失败", e);
        }
    }

    private static double number(Number value) { return value == null ? 0.0 : value.doubleValue(); }

}
