package com.qzk.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qzk.dp.entity.UserInfo;
import com.qzk.dp.service.UserInfoService;
import com.qzk.dp.mapper.UserInfoMapper;
import org.springframework.stereotype.Service;

/**
* @author qianzhikang
* @description 针对表【tb_user_info】的数据库操作Service实现
* @createDate 2023-02-06 11:35:35
*/
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo>
    implements UserInfoService{

}




