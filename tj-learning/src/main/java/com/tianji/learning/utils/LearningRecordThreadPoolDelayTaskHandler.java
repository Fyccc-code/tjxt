package com.tianji.learning.utils;

import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mq.message.LearningRecordDelayMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 基于DelayQueue + 自定义线程池的多线程学习记录延迟任务处理器
 * 消费模型：调度线程阻塞监听DelayQueue，取到到期任务后提交给自定义线程池执行
 * （由 config.LearningRecordTaskExecutorConfig 提供七参数自定义线程池：
 * 弹性线程 core~max、有界队列缓冲、CallerRunsPolicy背压、空闲回收）
 * 相比单线程版（LearningRecordDelayTaskHandler）的改进：
 * 1.任务处理多线程并行，线程数随负载弹性伸缩，空闲时不占资源
 * 2.任务处理委托给独立Bean LearningRecordTaskProcessor，每任务一个事务
 * 3.任务乱序安全性：并发下旧任务可能后执行，但"比对缓存moment"的判断保证旧数据被丢弃
 * 通过配置项 tianji.learning.record-delay-type = delayqueue-pool 启用
 *
 * @author Fyc
 * @since 2026-11-17 21:38:52
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tianji.learning.record-delay-type", havingValue = "delayqueue-pool")
public class LearningRecordThreadPoolDelayTaskHandler implements LearningRecordTaskHandler {

    private final LearningRecordTaskProcessor taskProcessor;
    //自定义线程池（按Bean名称注入 LearningRecordTaskExecutorConfig#recordDelayTaskExecutor）
    private final ThreadPoolExecutor recordDelayTaskExecutor;
    private final DelayQueue<DelayTask<LearningRecordDelayMessage>> queue = new DelayQueue<>();
    //延迟时间
    private static final Duration RECORD_DELAY_TIME = Duration.ofSeconds(20);
    //volatile 保证多线程间的可见性
    private static volatile boolean begin = true;
    //调度线程：唯一负责从DelayQueue取任务，转交给线程池
    private Thread dispatcher;

    public LearningRecordThreadPoolDelayTaskHandler(LearningRecordTaskProcessor taskProcessor,
                                                    ThreadPoolExecutor recordDelayTaskExecutor) {
        this.taskProcessor = taskProcessor;
        this.recordDelayTaskExecutor = recordDelayTaskExecutor;
    }

    @PostConstruct
    public void init() {
        //单调度线程 + 自定义线程池方案：调度线程只负责取任务，处理交给线程池
        dispatcher = new Thread(this::dispatchDelayTask, "record-delay-dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();
        log.debug("学习记录延迟任务调度线程已启动，处理线程池：{}", recordDelayTaskExecutor);
    }

    @PreDestroy
    public void destroy() {
        begin = false;
        //中断阻塞在take()上的调度线程，让while循环退出
        dispatcher.interrupt();
        //线程池由Spring销毁Bean时自动shutdown，等待已提交任务执行完
        log.debug("延迟任务停止执行！");
    }

    private void dispatchDelayTask() {
        while (begin) {
            try {
                // 1.阻塞获取到期的延迟任务（只有调度线程访问，无并发问题）
                DelayTask<LearningRecordDelayMessage> task = queue.take();
                // 2.提交给自定义线程池执行（线程数弹性伸缩；队列满时CallerRunsPolicy由本线程执行，天然背压不丢任务）
                recordDelayTaskExecutor.execute(() -> taskProcessor.process(task.getData()));
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
        // 2.提交延迟任务到延迟队列 DelayQueue
        queue.add(new DelayTask<>(LearningRecordDelayMessage.from(record), RECORD_DELAY_TIME));
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
