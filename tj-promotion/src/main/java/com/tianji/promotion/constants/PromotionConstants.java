package com.tianji.promotion.constants;


/**
 * @author Fyc
 * @since 2025-11-20 20:59:02
 */

public interface PromotionConstants {
    String COUPON_CODE_SERIAL_KEY = "coupon:code:serial";
    String COUPON_CODE_MAP_KEY = "coupon:code:map";
    String COUPON_CACHE_KEY_PREFIX = "prs:coupon:";
    String USER_COUPON_CACHE_KEY_PREFIX = "prs:user:coupon:";
    String COUPON_RANGE_KEY = "prom:coupon:range";

    // 优惠券领取错误消息
    String[] RECEIVE_COUPON_ERROR_MSG = {
            "优惠券不存在",
            "优惠券未开始或已结束",
            "优惠券库存不足",
            "超出领取数量",
            "请求频繁，请稍后再试"
    };

    // 优惠券兑换错误消息
    String[] EXCHANGE_COUPON_ERROR_MSG = {
            "兑换码不存在",
            "兑换码已经被兑换过了",
            "优惠券活动未开始或已经结束",
            "超出领取数量",
            "请求频繁，请稍后再试"
    };
}
