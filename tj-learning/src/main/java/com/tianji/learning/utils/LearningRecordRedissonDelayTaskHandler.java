package com.tianji.learning.utils;

import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mq.message.LearningRecordDelayMessage;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redisson延迟队列的学习记录延迟任务处理器
 * 原理：RDelayedQueue负责暂存延迟消息，到期后自动转入RBlockingQueue，调度线程阻塞监听目标队列，
 * 取到任务后提交给自定义线程池（config.LearningRecordTaskExecutorConfig）执行
 * 相比JVM内DelayQueue的优势：
 * 1.任务存Redis，服务重启任务不丢
 * 2.多实例部署时共享同一个队列，天然负载均衡
 * 通过配置项 tianji.learning.record-delay-type = redisson 启用
 *
 * @author Fyc
 * @since 2025-11-17 21:38:52
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tianji.learning.record-delay-type", havingValue = "redisson")
public class LearningRecordRedissonDelayTaskHandler implements LearningRecordTaskHandler {

    private final RedissonClient redissonClient;
    private final LearningRecordTaskProcessor taskProcessor;
    //自定义线程池（按Bean名称注入 LearningRecordTaskExecutorConfig#recordDelayTaskExecutor）
    private final ThreadPoolExecutor recordDelayTaskExecutor;

    //延迟队列在Redis中的key
    private static final String DELAY_QUEUE_NAME = "learning:record:delay:queue";
    //延迟时间
    private static final Duration RECORD_DELAY_TIME = Duration.ofSeconds(20);
    //volatile 保证多线程间的可见性
    private static volatile boolean begin = true;

    //目标队列：到期任务的存放地，调度线程监听这里
    private RBlockingQueue<LearningRecordDelayMessage> blockingQueue;
    //延迟队列：暂存延迟任务，到期后自动转入目标队列
    private RDelayedQueue<LearningRecordDelayMessage> delayedQueue;
    //调度线程：唯一负责从目标队列取任务，转交给线程池
    private Thread dispatcher;

    public LearningRecordRedissonDelayTaskHandler(RedissonClient redissonClient,
                                                  LearningRecordTaskProcessor taskProcessor,
                                                  ThreadPoolExecutor recordDelayTaskExecutor) {
        this.redissonClient = redissonClient;
        this.taskProcessor = taskProcessor;
        this.recordDelayTaskExecutor = recordDelayTaskExecutor;
    }

    @PostConstruct
    public void init() {
        // 1.初始化Redisson延迟队列（先定义目标队列，再基于它创建延迟队列）
        blockingQueue = redissonClient.getBlockingQueue(DELAY_QUEUE_NAME);
        delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        // 2.启动调度线程，阻塞监听目标队列
        dispatcher = new Thread(this::dispatchDelayTask, "record-redisson-dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();
        log.debug("学习记录Redisson延迟队列调度线程已启动，处理线程池：{}", recordDelayTaskExecutor);
    }

    @PreDestroy
    public void destroy() {
        begin = false;
        //中断阻塞在take()上的调度线程
        dispatcher.interrupt();
        //销毁延迟队列，停止Redisson内部的到期搬运调度任务
        delayedQueue.destroy();
        //线程池由Spring销毁Bean时自动shutdown，等待已提交任务执行完
        log.debug("延迟任务停止执行！");
    }

    private void dispatchDelayTask() {
        while (begin) {
            try {
                // 1.阻塞获取到期任务（多实例时，各实例的调度线程竞争消费同一队列，天然负载均衡）
                LearningRecordDelayMessage message = blockingQueue.take();
                // 2.提交给自定义线程池执行（线程数弹性伸缩；队列满时CallerRunsPolicy由本线程执行，天然背压不丢任务）
                recordDelayTaskExecutor.execute(() -> taskProcessor.process(message));
            } catch (InterruptedException e) {
                //被中断，恢复中断标志让while条件退出
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("提交延迟任务到线程池发生异常", e);
            }
        }
    }

    @Override
    public void addLearningRecordTask(LearningRecord record) {
        // 1.添加数据到Redis缓存
        taskProcessor.writeRecordCache(record);
        // 2.提交延迟任务到Redisson延迟队列，20秒后自动转入目标队列
        delayedQueue.offer(LearningRecordDelayMessage.from(record),
                RECORD_DELAY_TIME.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void writeRecordCache(LearningRecord record) {
        taskProcessor.writeRecordCache(record);
    }

    @Override
    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        return taskProcessor.readRecordCache(lessonId, sectionId);
    }

    @Override
    public void cleanRecordCache(Long lessonId, Long sectionId) {
        taskProcessor.cleanRecordCache(lessonId, sectionId);
    }
}
