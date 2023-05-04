package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询笔记
     * @param id 笔记id
     * @return Result
     */
    Result queryBlogById(Long id);

    /**
     * 查询热门笔记
     * @param current 当前页
     * @return Result
     */
    Result queryHotBlog(Integer current);

    /**
     * 点赞
     * @param id id
     * @return Result
     */
    Result likeBlog(Long id);

    /**
     * 查询top5点赞用户
     * @param id 笔记id
     * @return Result
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存
     * @param blog 笔记
     * @return Result
     */
    Result saveBlog(Blog blog);

    /**
     * 分页查询
     * @param max 最大
     * @param offset 偏移量
     * @return Result
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
