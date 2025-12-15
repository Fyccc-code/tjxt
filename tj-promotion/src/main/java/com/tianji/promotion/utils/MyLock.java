package com.tianji.promotion.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * @author Fyc
 * @since 2025/11/22 20:27:10
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MyLock {
    String name();

    long waitTime() default 1;

    //锁超时施放时间
    long leaseTime() default -1;

    TimeUnit unit() default TimeUnit.SECONDS;

    MyLockType type() default MyLockType.RE_ENTRANT_LOCK;

    MyLockStrategy strategy() default MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT;
}