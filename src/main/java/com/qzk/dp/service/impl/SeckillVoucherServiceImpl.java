package com.qzk.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qzk.dp.entity.SeckillVoucher;
import com.qzk.dp.service.SeckillVoucherService;
import com.qzk.dp.mapper.SeckillVoucherMapper;
import org.springframework.stereotype.Service;

/**
* @author qianzhikang
* @description 针对表【tb_seckill_voucher(秒杀优惠券表，与优惠券是一对一关系)】的数据库操作Service实现
* @createDate 2023-02-06 11:35:35
*/
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher>
    implements SeckillVoucherService{

}




