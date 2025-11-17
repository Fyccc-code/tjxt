package com.tianji.learning.handle;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Fyc
 * @date 2025/11/06 18:22:36
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService seasonService;
    private final IPointsBoardService pointsBoardService;
    private final StringRedisTemplate redisTemplate;

    //@Scheduled(cron = "0 0 3 1 * ?")
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason() {
        //1.获取上个月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        //2.查询赛季id
        Integer season = seasonService.querySeasonByTime(time);
        if (season == null) {
            //赛季不存在
            log.debug("赛季不存在，无法创建历史排行榜");
            return;
        }
        //3.创建上个月排行榜 创建表
        pointsBoardService.createPointsBoardTableBySeason(season);
    }

    /**
     * 持久化积分榜数据
     */
    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB() {
        //1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        //2.计算动态表名
        //查询赛季信息
        Integer season = seasonService.querySeasonByTime(time);
        // 将表名存入ThreadLocal
        TableInfoContext.setInfo(LearningConstants.POINTS_BOARD_TABLE_PREFIX + season);
        //查询榜单数据
        //2.1拼接key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        //查询数据
        int index = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        int pageNo = index + 1;
        int pageSize = 100;
        while (true) {
            List<PointsBoard> list = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(list)) {
                break;
            }
            //4.持久化到数据库
            //4.1.排名写入id
            list.forEach(p -> {
                p.setId(p.getRank().longValue());
                p.setRank(null);
            });
            //4.2.持久化
            pointsBoardService.saveBatch(list);
            //5.下一页
            pageNo += total;
        }
        //清除ThreadLocal
        TableInfoContext.remove();
    }

    /**
     * 从 Redis中清除积分板
     */
    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis() {
        //获取当前时间
        LocalDateTime time = LocalDateTime.now().minusDays(1);
        //拼接key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        //删除key
        redisTemplate.unlink(key);
    }
}
