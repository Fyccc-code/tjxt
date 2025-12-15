package com.tianji.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author Fyc
 * @since 2025-11-17
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateCode(Coupon coupon);

    boolean updateExchangeMark(long serialNum, boolean b);
}
