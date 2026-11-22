package com.sky.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.exception.BaseException;
import com.sky.properties.WeChatProperties;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WeChatLoginClient {
    private final WeChatProperties properties;

    public WeChatLoginClient(WeChatProperties properties) {
        this.properties = properties;
    }

    public String getOpenid(String code) {
        try {
            URIBuilder uri = new URIBuilder("https://api.weixin.qq.com/sns/jscode2session")
                    .addParameter("appid", properties.getAppid())
                    .addParameter("secret", properties.getSecret())
                    .addParameter("js_code", code)
                    .addParameter("grant_type", "authorization_code");
            RequestConfig config = RequestConfig.custom().setConnectTimeout(5000)
                    .setConnectionRequestTimeout(5000).setSocketTimeout(5000).build();
            HttpGet request = new HttpGet(uri.build());
            request.setConfig(config);
            try (CloseableHttpClient client = HttpClients.createDefault();
                 CloseableHttpResponse response = client.execute(request)) {
                if (response.getStatusLine().getStatusCode() != 200 || response.getEntity() == null) {
                    throw new BaseException("微信登录失败，请稍后重试");
                }
                return parseOpenid(EntityUtils.toString(response.getEntity(), "UTF-8"));
            }
        } catch (Exception ex) {
            throw new BaseException("微信登录失败，请稍后重试");
        }
    }
    String parseOpenid(String json) {
        try {
            JSONObject body = JSON.parseObject(json);
            Integer error = body == null ? null : body.getInteger("errcode");
            if (body == null || (error != null && error != 0) || !StringUtils.hasText(body.getString("openid"))) {
                throw new BaseException("微信登录失败，请稍后重试");
            }
            return body.getString("openid");
        } catch (Exception ex) {
            throw new BaseException("微信登录失败，请稍后重试");
        }
    }}
