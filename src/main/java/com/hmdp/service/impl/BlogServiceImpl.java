package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScoreResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService iUserService;

    @Resource
    private IFollowService iFollowService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询笔记
     *
     * @param id 笔记id
     * @return Result
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        // 查询用户是否点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 查询热门笔记
     *
     * @param current 当前页
     * @return Result
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 点赞
     *
     * @param id id
     * @return Result
     */
    @Override
    public Result likeBlog(Long id) {
        // 判断用户是否点赞
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 未点赞
            // 点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                // 保存用户到redis
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 已点赞，取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                // 移除redis的set集合内用户id
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查询top5点赞用户
     *
     * @param id 笔记id
     * @return Result
     */
    @Override
    public Result queryBlogLikes(Long id) {
        // key
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 查询redis中按时间戳排名的前五
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // 空值处理
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 查询用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idsStr = StrUtil.join(",", ids);
        // 查询用户
        List<UserDTO> collect = iUserService
                .query()
                .in("id", ids)
                // 解决数据库排序问题 in 排序给定值之后，查询结果按 id 升序
                // 方案为在语句后加入 ORDER BY FIELD(id,"id集合串") 指定顺序
                .last("ORDER BY FIELD(id," + idsStr + ")")
                .list()
                .stream()
                .map(item -> BeanUtil.copyProperties(item, UserDTO.class))
                .collect(Collectors.toList());
        // 返回
        return Result.ok(collect);
    }

    /**
     * 保存
     *
     * @param blog 笔记
     * @return Result
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        save(blog);
        // 查询粉丝id
        List<Follow> follows = iFollowService.query().eq("follow_user_id", user.getId()).list();
        // 推送消息
        for (Follow follow : follows) {
            // 推送
            Long userId = follow.getUserId();
            String key = "feed:" + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 分页查询
     *
     * @param max    最大
     * @param offset 偏移量
     * @return Result
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 查询收件箱
        String key = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>();
        // 最小时间，第一次默认为0
        long minTime = 0;
        // 偏移量
        int os = 1;
        // 解析数据
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            // 获取分数(时间戳)
            long time = typedTuple.getScore().longValue();
            // 若最小时间戳有相等
            if (time == minTime) {
                // 偏移量++
                os++;
            }else {
                // 最小时间重制
                minTime = time;
                // 重制偏移量
                os = 1;
            }
        }
        // 将id集合转成 ，隔开的字符串
        String idStr = StrUtil.join(",", ids);
        // 按id顺序查询
        List<Blog> blogs = query().in("id",ids).last("ORDER BY FIELD(id," + idStr + ")" ).list();
        // 查询笔记作者和点赞记录
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        // 封装滚动分页结果
        ScoreResult scoreResult = new ScoreResult();
        scoreResult.setList(blogs);
        scoreResult.setMinTime(minTime);
        scoreResult.setOffset(os);
        return Result.ok(blogs);
    }

    /**
     * 查询笔记作者
     *
     * @param blog 笔记
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = iUserService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 查询用户是否点赞
     *
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登陆
            return;
        }
        // 判断用户是否点赞
        Long userId = user.getId();
        String key = "blog:key:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

}
