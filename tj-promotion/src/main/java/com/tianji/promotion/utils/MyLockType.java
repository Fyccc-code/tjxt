package com.tianji.promotion.utils;

/**
 * @author Fyc
 * @since 2025/11/22 20:39:23
 */
public enum MyLockType {
    RE_ENTRANT_LOCK, // 可重入锁
    FAIR_LOCK, // 公平锁
    READ_LOCK, // 读锁
    WRITE_LOCK, // 写锁
    ;
}