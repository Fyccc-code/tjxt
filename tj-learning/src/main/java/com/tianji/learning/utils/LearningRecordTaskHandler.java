package com.tianji.learning.utils;

import com.tianji.learning.domain.po.LearningRecord;

/**
 * 学习记录延迟任务处理器接口
 * 实现方式（通过配置项 tianji.learning.record-delay-type 切换）：
 * 1. delayqueue（默认）：单线程 + JVM内存DelayQueue，见 {@link LearningRecordDelayTaskHandler}
 * 2. delayqueue-pool：线程池多线程 + JVM内存DelayQueue，见 {@link LearningRecordThreadPoolDelayTaskHandler}
 * 3. mq：RabbitMQ延迟消息，见 com.tianji.learning.mq.LearningRecordMqTaskHandler
 * 4. redisson：Redisson分布式延迟队列，任务存Redis、多实例共享，见 {@link LearningRecordRedissonDelayTaskHandler}
 * 除默认版外，其余版本的持久化与缓存逻辑统一委托给 {@link LearningRecordTaskProcessor}（每任务一个事务）
 *
 * @author Fyc
 * @since 2026-11-16 21:38:52
 */

public interface LearningRecordTaskHandler {
    /**
     * 提交学习记录延迟任务：写Redis缓存，并在延迟时间后持久化播放进度
     */
    void addLearningRecordTask(LearningRecord record);

    /**
     * 写入学习记录缓存
     */
    void writeRecordCache(LearningRecord record);

    /**
     * 读取学习记录缓存
     */
    LearningRecord readRecordCache(Long lessonId, Long sectionId);

    /**
     * 清理学习记录缓存
     */
    void cleanRecordCache(Long lessonId, Long sectionId);
}
