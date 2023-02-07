package com.qzk.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.qzk.dp.dto.LoginFormDTO;
import com.qzk.dp.dto.Result;
import com.qzk.dp.dto.UserDTO;
import com.qzk.dp.entity.User;
import com.qzk.dp.mapper.UserMapper;
import com.qzk.dp.service.IUserService;
import com.qzk.dp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     *
     * @param phone   手机号
     * @param session sessionid
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.不符合返回错误
            return Result.fail("手机号格式错误");
        }

        // 3. 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码到session
        //session.setAttribute("code", code);
        // 4. 保存到redis
        stringRedisTemplate.opsForValue().set("login:code:" + phone, code, 2, TimeUnit.MINUTES);
        // 5.发送验证码(模拟)
        log.info("发送验证码成功，验证码{}", code);

        // 6.返回ok
        return Result.ok();
    }

    /**
     * 实现用户登陆
     *
     * @param loginForm 登陆表单
     * @param session   session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 验证手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 获取验证码code从session
        //Object cacheCode = session.getAttribute("code");

        // 从redis中获取code
        String cacheCode = stringRedisTemplate.opsForValue().get("login:code:" + phone);

        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 查询数据库中用户
        User user = query().eq("phone", phone).one();

        // 不存在
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 存在session
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        //生成token作为令牌
        String token = UUID.randomUUID().toString(true);
        //转为hash存储在redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String redisToken = "login:token:" + token;
        // 以hash存储在redis
        stringRedisTemplate.opsForHash().putAll(redisToken, map);
        // 设置过期时间
        stringRedisTemplate.expire(redisToken, 30, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
