package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Orders;
import com.sky.exception.BaseException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DailyOrderCountVO;
import com.sky.vo.DailyTurnoverVO;
import com.sky.vo.DailyUserCountVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class WorkspaceServiceImpl implements WorkspaceService {
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;

    public WorkspaceServiceImpl(OrderMapper orderMapper, UserMapper userMapper, DishMapper dishMapper, SetmealMapper setmealMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
    }

    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime endExclusive) {
        if (begin == null || endExclusive == null || !begin.isBefore(endExclusive)) throw new BaseException("统计时间范围无效");
        long total = 0, valid = 0, users = 0;
        BigDecimal turnover = BigDecimal.ZERO;
        // 复用已验收的报表聚合与左闭右开时间范围。
        for (DailyOrderCountVO row : orderMapper.countOrdersByDate(begin, endExclusive, Orders.COMPLETED)) {
            if (row.getTotalOrders() != null) total += row.getTotalOrders();
            if (row.getValidOrders() != null) valid += row.getValidOrders();
        }
        for (DailyTurnoverVO row : orderMapper.sumTurnoverByDate(begin, endExclusive, Orders.COMPLETED)) {
            if (row.getTurnover() != null) turnover = turnover.add(row.getTurnover());
        }
        for (DailyUserCountVO row : userMapper.countDailyUsers(begin, endExclusive)) {
            if (row.getNewUsers() != null) users += row.getNewUsers();
        }
        if (valid > Integer.MAX_VALUE || users > Integer.MAX_VALUE) throw new BaseException("统计数量超出范围");
        double amount = turnover.doubleValue();
        return BusinessDataVO.builder().turnover(amount).validOrderCount((int) valid)
                .orderCompletionRate(total == 0 ? 0.0 : (double) valid / total)
                .unitPrice(valid == 0 ? 0.0 : amount / valid).newUsers((int) users).build();
    }

    public OrderOverViewVO getOrderOverView() {
        // 沿用课程：统计当天零点之后下单的订单，待派送对应已接单。
        return orderMapper.getWorkspaceOrderOverview(LocalDate.now().atStartOfDay(), Orders.TO_BE_CONFIRMED,
                Orders.CONFIRMED, Orders.COMPLETED, Orders.CANCELLED);
    }

    public DishOverViewVO getDishOverView() { return dishMapper.getWorkspaceOverview(StatusConstant.ENABLE, StatusConstant.DISABLE); }
    public SetmealOverViewVO getSetmealOverView() { return setmealMapper.getWorkspaceOverview(StatusConstant.ENABLE, StatusConstant.DISABLE); }
}
