package com.tianji.learning.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.DelayQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Fyc
 * @since 2025-10-26 15:05:13
 */

@Slf4j
class DelayTaskTest {

    @Test
    void testDelayQueue() throws InterruptedException {
        DelayQueue<DelayTask<String>> queue = new DelayQueue<>();
        log.info("开始初始化延迟任务。。。");
        queue.add(new DelayTask<>("延迟任务3", Duration.ofSeconds(3)));
        queue.add(new DelayTask<>("延迟任务2", Duration.ofSeconds(2)));
        queue.add(new DelayTask<>("延迟任务1", Duration.ofSeconds(1)));
        //尝试执行任务
        while (true) {
            DelayTask<String> task = queue.take();
            log.info("开始执行延迟任务：{}", task.getData());
        }
    }
}