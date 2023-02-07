package com.qzk.dp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.qzk.dp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Description 刷新token拦截器
 * @Date 2023-02-07-09-46
 * @Author qianzhikang
 */
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取session
        //HttpSession session = request.getSession();
        // 获取session用户
        //Object user = session.getAttribute("user");
        // 基于token获取redis中的用户
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries("login:token:" + token);

        // 用户不存在
        if (userMap.isEmpty()) {
            return true;
        }
        // 从map中赋值转换
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 存储user信息在ThreadLocal
        UserHolder.saveUser(userDTO);
        // 刷新redis 用户信息过期时间
        stringRedisTemplate.expire("login:token:" + token,30, TimeUnit.MINUTES);
        //// 用户不存在
        //if (user == null){
        //    response.setStatus(401);
        //    return false;
        //}
        // 存在，保存在ThreadLocal
        //UserHolder.saveUser((UserDTO) user);

        // 放行
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
