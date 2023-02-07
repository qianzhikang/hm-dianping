package com.qzk.dp.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qzk.dp.entity.SeckillVoucher;
import com.qzk.dp.mapper.SeckillVoucherMapper;
import com.qzk.dp.service.ISeckillVoucherService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
