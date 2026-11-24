package com.tianji.learning.mq.message;

import com.tianji.learning.domain.po.LearningRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 学习记录延迟任务消息体
 *
 * @author Fyc
 * @since 2025-11-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class LearningRecordDelayMessage {
    /*课表id*/
    private Long lessonId;
    /*小节id*/
    private Long sectionId;
    /*视频播放进度，单位秒*/
    private Integer moment;

    public static LearningRecordDelayMessage from(LearningRecord record) {
        return new LearningRecordDelayMessage(record.getLessonId(), record.getSectionId(), record.getMoment());
    }
}
