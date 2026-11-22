package com.sky.service.impl;

import com.sky.client.WeChatLoginClient;
import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.BaseException;
import com.sky.mapper.UserMapper;
import com.sky.properties.JwtProperties;
import com.sky.service.UserService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.Collections;

@Service
public class UserServiceImpl implements UserService {
    private final WeChatLoginClient weChatLoginClient;
    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;

    public UserServiceImpl(WeChatLoginClient weChatLoginClient, UserMapper userMapper, JwtProperties jwtProperties) {
        this.weChatLoginClient = weChatLoginClient;
        this.userMapper = userMapper;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public UserLoginVO login(UserLoginDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getCode())) {
            throw new BaseException("微信登录凭证不能为空");
        }
        String openid = weChatLoginClient.getOpenid(dto.getCode());
        if (!StringUtils.hasText(openid)) {
            throw new BaseException("微信登录失败，请稍后重试");
        }
        User user = userMapper.getByOpenid(openid);
        if (user == null) {
            user = User.builder().openid(openid).createTime(LocalDateTime.now()).build();
            userMapper.insert(user);
        }
        if (user.getId() == null) {
            throw new BaseException("微信登录失败，请稍后重试");
        }
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(),
                new java.util.HashMap<>(Collections.singletonMap(JwtClaimsConstant.USER_ID, user.getId())));
        return UserLoginVO.builder().id(user.getId()).openid(openid).token(token).build();
    }
}
