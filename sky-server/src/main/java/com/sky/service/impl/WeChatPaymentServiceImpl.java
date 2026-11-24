package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.entity.Orders;
import com.sky.entity.User;
import com.sky.exception.BaseException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WeChatPaymentService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WeChatPaymentServiceImpl implements WeChatPaymentService {
    private final OrderMapper orders;
    private final UserMapper users;
    private final WeChatPayUtil payUtil;
    private final boolean enabled;
    public WeChatPaymentServiceImpl(OrderMapper orders, UserMapper users, WeChatPayUtil payUtil,
                                    @Value("${sky.wechat.payment-enabled:false}") boolean enabled) {
        this.orders = orders; this.users = users; this.payUtil = payUtil; this.enabled = enabled;
    }
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO dto) {
        if (!enabled) throw new BaseException("微信支付暂未启用");
        // 回调验签尚未接入，禁止发起真实交易，避免付款后无法安全确认订单。
        throw new BaseException("微信支付暂不可用：支付通知验证尚未就绪");
    }
}
