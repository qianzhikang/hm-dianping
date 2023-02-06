package com.qzk.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qzk.dp.entity.User;
import com.qzk.dp.service.UserService;
import com.qzk.dp.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
* @author qianzhikang
* @description 针对表【tb_user】的数据库操作Service实现
* @createDate 2023-02-06 11:35:35
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

}




