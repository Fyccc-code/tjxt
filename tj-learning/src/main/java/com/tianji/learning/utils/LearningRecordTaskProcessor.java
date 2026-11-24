package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.mq.message.LearningRecordDelayMessage;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 学习记录延迟任务的统一处理器
 * 独立Bean存在的意义：
 * 1.每处理一个任务开一个独立事务，"更新学习记录 + 更新课表"两步原子提交
 * （不能把事务加在消费循环方法上：自调用绕过代理 + while死循环会成为永不提交的事务）
 * 2.线程池版、Redisson版共用持久化和缓存逻辑，避免重复代码
 *
 * @author Fyc
 * @since 2025-11-17 21:38:52
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningRecordTaskProcessor {

    private final StringRedisTemplate redisTemplate;
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;

    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";

    /**
     * 处理单个延迟任务，一个任务一个事务
     */
    @Transactional(rollbackFor = Exception.class)
    public void process(LearningRecordDelayMessage message) {
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
    }

    /**
     * 写入学习记录缓存
     */
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

    /**
     * 读取学习记录缓存
     */
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

    /**
     * 清理学习记录缓存
     */
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
