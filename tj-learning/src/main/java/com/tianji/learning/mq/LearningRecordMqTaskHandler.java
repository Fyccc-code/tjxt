package com.tianji.learning.mq;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.mq.message.LearningRecordDelayMessage;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.utils.LearningRecordTaskHandler;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 基于RabbitMQ延迟消息的学习记录延迟任务处理器
 * 通过配置项 tianji.learning.record-delay-type = mq 启用
 *
 * @author Fyc
 * @since 2025-11-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tianji.learning.record-delay-type", havingValue = "mq")
public class LearningRecordMqTaskHandler implements LearningRecordTaskHandler {

    private final StringRedisTemplate redisTemplate;
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;
    private final RabbitMqHelper mqHelper;
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";
    //延迟时间，与DelayQueue版本保持一致
    private static final Duration RECORD_DELAY_TIME = Duration.ofSeconds(20);

    @Override
    public void addLearningRecordTask(LearningRecord record) {
        // 1.添加数据到Redis缓存
        writeRecordCache(record);
        // 2.发送延迟消息到MQ，20秒后投递给消费者
        mqHelper.sendDelayMessage(
                MqConstants.Exchange.LEARNING_DELAY_EXCHANGE,
                MqConstants.Key.RECORD_DELAY_KEY,
                LearningRecordDelayMessage.from(record),
                RECORD_DELAY_TIME
        );
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "learning.delay.record.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_DELAY_EXCHANGE, delayed = "true", type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.RECORD_DELAY_KEY
    ))
    public void listenLearningRecordDelayMessage(LearningRecordDelayMessage message) {
        log.debug("收到学习记录延迟任务消息：{}", message);
        try {
            // 1.查询Redis缓存
            LearningRecord record = readRecordCache(message.getLessonId(), message.getSectionId());
            if (record == null) {
                return;
            }
            // 2.比较数据，moment值
            if (!Objects.equals(message.getMoment(), record.getMoment())) {
                // 不一致，说明用户还在持续提交播放进度，放弃旧数据
                return;
            }
            // 3.一致，持久化播放进度数据到数据库
            // 3.1.更新学习记录的moment
            record.setFinished(null);
            recordMapper.updateById(record);
            // 3.2.更新课表最近学习信息
            LearningLesson lesson = new LearningLesson();
            lesson.setId(message.getLessonId());
            lesson.setLatestSectionId(message.getSectionId());
            lesson.setLatestLearnTime(LocalDateTime.now());
            lessonService.updateById(lesson);
        } catch (Exception e) {
            log.error("处理学习记录延迟消息发生异常", e);
        }
    }

    @Override
    public void writeRecordCache(LearningRecord record) {
        log.debug("更新学习记录的缓存数据");
        try {
            // 1.数据转换
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            // 2.写入Redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(), json);
            // 3.添加缓存过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("更新学习记录缓存异常", e);
        }
    }

    @Override
    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        try {
            // 1.读取Redis数据
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            Object cacheData = redisTemplate.opsForHash().get(key, sectionId.toString());
            // 2.数据检查和转换
            return cacheData == null ? null : JsonUtils.toBean(cacheData.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("缓存读取异常", e);
            return null;
        }
    }

    @Override
    public void cleanRecordCache(Long lessonId, Long sectionId) {
        // 删除数据
        String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
        redisTemplate.opsForHash().delete(key, sectionId.toString());
    }

    @Data
    @NoArgsConstructor
    private static class RecordCacheData {
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }
}
