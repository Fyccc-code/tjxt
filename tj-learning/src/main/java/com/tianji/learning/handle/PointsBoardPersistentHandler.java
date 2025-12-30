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
        //分片 第几片 分片起始页码就是数据分片的编号
        int index = XxlJobHelper.getShardIndex();
        //总共有几片 分页跨度就是分片的总数量
        int total = XxlJobHelper.getShardTotal();
        int pageNo = index + 1;
        int pageSize = 100;
        //循环查数据库并且持久化
        while (true) {
            //数据可能比较多 带宽 调用service分页查询
            List<PointsBoard> list = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(list)) {
                //查出来为空 跳出循环
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
        //删除key unlink异步删除 开启单独的线程去删除 防止主线程阻塞
        redisTemplate.unlink(key);
    }
}
