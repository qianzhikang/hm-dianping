package com.qzk.dp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @Description 登陆拦截器
 * @Date 2023-02-07-09-46
 * @Author qianzhikang
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 拦截没有登陆的请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (UserHolder.getUser() == null){
            response.setStatus(401);
            return false;
        }
        // 放行
        return true;
    }
}
