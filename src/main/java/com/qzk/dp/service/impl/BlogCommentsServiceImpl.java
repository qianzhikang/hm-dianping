package com.qzk.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qzk.dp.entity.BlogComments;
import com.qzk.dp.service.BlogCommentsService;
import com.qzk.dp.mapper.BlogCommentsMapper;
import org.springframework.stereotype.Service;

/**
* @author qianzhikang
* @description 针对表【tb_blog_comments】的数据库操作Service实现
* @createDate 2023-02-06 11:35:35
*/
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments>
    implements BlogCommentsService{

}




