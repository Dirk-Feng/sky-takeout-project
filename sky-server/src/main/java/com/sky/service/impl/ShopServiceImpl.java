package com.sky.service.impl;

import com.sky.constant.RedisConstant;
import com.sky.constant.StatusConstant;
import com.sky.exception.BaseException;
import com.sky.service.ShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ShopServiceImpl implements ShopService {
    private final StringRedisTemplate redisTemplate;

    public ShopServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setStatus(Integer status) {
        if (!StatusConstant.ENABLE.equals(status) && !StatusConstant.DISABLE.equals(status)) {
            throw new BaseException("营业状态无效");
        }
        // 使用字符串序列化，Redis CLI 可直接读取 0 或 1。
        redisTemplate.opsForValue().set(RedisConstant.SHOP_STATUS, status.toString());
    }

    public Integer getStatus() {
        String status = redisTemplate.opsForValue().get(RedisConstant.SHOP_STATUS);
        if (status == null) throw new BaseException("营业状态尚未设置");
        if ("1".equals(status)) return StatusConstant.ENABLE;
        if ("0".equals(status)) return StatusConstant.DISABLE;
        throw new BaseException("营业状态数据无效");
    }
}
