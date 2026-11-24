package com.tianji.learning.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 学习记录延迟任务的自定义线程池
 * 七参数全参构造，参数均可通过配置文件覆盖：
 * tianji:
 * learning:
 * record-task:
 * core-size: 4          核心线程数
 * max-size: 8           最大线程数
 * keep-alive: 60        非核心线程空闲存活时间（秒）
 * queue-capacity: 100   有界队列容量（缓冲待处理任务）
 * thread-name-prefix: record-task-worker-
 * <p>
 * 设计说明：
 * 1.有界队列：防止任务堆积撑爆内存（Executors.newFixedThreadPool的无界队列是OOM隐患）
 * 2.CallerRunsPolicy：队列满时由提交任务的调度线程自己执行，天然背压，不丢任务
 * 3.allowCoreThreadTimeOut：空闲时核心线程也回收，没有延迟任务时不占资源
 *
 * @author Fyc
 * @since 2025-11-17 21:38:52
 */
@Slf4j
@Configuration
public class LearningRecordTaskExecutorConfig {

    @Bean("recordDelayTaskExecutor")
    public ThreadPoolExecutor recordDelayTaskExecutor(
            @Value("${tianji.learning.record-task.core-size:4}") int coreSize,
            @Value("${tianji.learning.record-task.max-size:8}") int maxSize,
            @Value("${tianji.learning.record-task.keep-alive:60}") long keepAlive,
            @Value("${tianji.learning.record-task.queue-capacity:100}") int queueCapacity,
            @Value("${tianji.learning.record-task.thread-name-prefix:record-task-worker-}") String threadNamePrefix) {
        // 1.自定义线程工厂：命名 + 守护线程，方便排查问题且不阻止JVM退出
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, threadNamePrefix + index.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        // 2.七参数自定义线程池
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreSize, maxSize, keepAlive, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
        // 3.空闲时核心线程也允许回收
        executor.allowCoreThreadTimeOut(true);
        log.debug("学习记录延迟任务线程池初始化完成：core={}, max={}, queue={}", coreSize, maxSize, queueCapacity);
        return executor;
    }
}
