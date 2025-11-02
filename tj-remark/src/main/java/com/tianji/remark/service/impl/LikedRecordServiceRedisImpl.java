package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;


/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author Fyc
 * @since 2025-10-29
 */
@Service
@RequiredArgsConstructor
public class LikedRecordServiceRedisImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void addLikedRecord(LikeRecordFormDTO recordDTO) {
        //1.基于前端的参数 判断是执行点赞还是取消点赞
        boolean success = recordDTO.getLiked() ? like(recordDTO) : unlike(recordDTO);
        //2.判断是否执行成功 失败的话直接结束
        if (!success) {
            return;
        }
        //3.执行成功 统计点赞总数
        Long likeTimes = redisTemplate.opsForSet().
                size(RedisConstants.LIKES_BIZ_KEY_PREFIX + recordDTO.getBizId());
        if (likeTimes == null) {
            return;
        }
        //4.缓存点赞总数到Redis
        redisTemplate.opsForZSet().add(
                RedisConstants.LIKES_TIMES_KEY_PREFIX + recordDTO.getBizType(),
                recordDTO.getBizId().toString(),
                likeTimes
        );
    }

    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        //1.获取登录用户id
        Long userId = UserContext.getUser();
        //2.查询点赞状态
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        //3.返回结果
       /* Set<Long> set = new HashSet<>();
        for (int i = 0; i < objects.size(); i++) {
            Boolean o = (Boolean) objects.get(i);
            if(o) {
                set.add(bizIds.get(i));
            }
        }

        Set<Long> collect = IntStream.range(0, objects.size())
                .filter(i -> (boolean) objects.get(i))
                .mapToObj(bizIds::get)
                .collect(Collectors.toSet());*/
        return IntStream.range(0, objects.size())
                .filter(i -> (boolean) objects.get(i))
                .mapToObj(bizIds::get)
                .collect(Collectors.toSet());
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        //1.读取并移除Redis缓存中的点赞总数
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, maxBizSize);
        if (CollUtils.isEmpty(tuples)) {
            return;
        }
        //2.数据转换
        List<LikeTimesDTO> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String bizId = tuple.getValue();
            Double likedTimes = tuple.getScore();
            if (bizId == null || likedTimes == null) {
                continue;
            }
            list.add(LikeTimesDTO.of(Long.valueOf(bizId), likedTimes.intValue()));
        }
        //3.发送MQ消息
        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, bizType),
                list);
    }

    private boolean unlike(LikeRecordFormDTO recordDTO) {
        //1.获取登录用户id
        Long userId = UserContext.getUser();
        //2.获取key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordDTO.getBizId();
        //3.执行srem命令
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        //result != null防止空指针
        return result != null && result > 0;
    }

    private boolean like(LikeRecordFormDTO recordDTO) {
        //1.获取登录用户id
        Long userId = UserContext.getUser();
        //2.获取key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordDTO.getBizId();
        //3.执行sadd命令
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        //result != null防止空指针
        return result != null && result > 0;
    }
}
