package com.qzk.dp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qzk.dp.dto.LoginFormDTO;
import com.qzk.dp.dto.Result;
import com.qzk.dp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送手机验证码
     * @param phone   手机号
     * @param session sessionid
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 实现用户登陆
     * @param loginForm 登陆表单
     * @param session   session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);
}
