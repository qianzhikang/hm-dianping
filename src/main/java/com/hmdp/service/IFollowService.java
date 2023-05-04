package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注/取关
     * @param id 用户id
     * @param isFollow 是否关注
     * @return Result
     */
    Result follow(Long id, Boolean isFollow);

    /**
     * 是否关注
     * @param followUserId 关注用户id
     * @return Result
     */
    Result isFollow(Long followUserId);

    /**
     * 共同关注
     * @param id id
     * @return Result
     */
    Result followCommons(Long id);
}
