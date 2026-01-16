package com.tianji.promotion.utils;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import static com.tianji.promotion.utils.MyLockType.*;

/**
 * @author Fyc
 * @since 2025/11/22 20:43:28
 */
@Component
//@RequiredArgsConstructor
public class MyLockFactory {

    //private final RedissonClient redissonClient;
    //值是行为 有参有返回值就是function 函数式接口 入参string 返回值RLock
    private final Map<MyLockType, Function<String, RLock>> lockHandlers;

    public MyLockFactory(RedissonClient redissonClient) {
        //this.redissonClient = redissonClient;
        //不需要做哈希运算 简化get逻辑 提升性能
        // this.lockHandlers = new EnumMap<MyLockType, Function<String, RLock>>(MyLockType.class);
        this.lockHandlers = new EnumMap<>(MyLockType.class);
        this.lockHandlers.put(RE_ENTRANT_LOCK, redissonClient::getLock);
        this.lockHandlers.put(FAIR_LOCK, redissonClient::getFairLock);
        this.lockHandlers.put(READ_LOCK, name -> redissonClient.getReadWriteLock(name).readLock());
        this.lockHandlers.put(WRITE_LOCK, name -> redissonClient.getReadWriteLock(name).writeLock());
    }

    /*public RLock getLock(MyLockType lockType, String name) {
        RLock lock = null;
        switch (lockType) {
            case RE_ENTRANT_LOCK:
                lock = redissonClient.getLock(name);
                break;
            case FAIR_LOCK:
                lock = redissonClient.getFairLock(name);
                break;
            case READ_LOCK:
                lock = redissonClient.getReadWriteLock(name).readLock();
                break;
            case WRITE_LOCK:
                lock = redissonClient.getReadWriteLock(name).writeLock();
                break;
            default:
                throw new BizIllegalException("错误的锁的类型");
                return lock;
        }*/

    public RLock getLock(MyLockType lockType, String name) {
        return lockHandlers.get(lockType).apply(name);
    }
}