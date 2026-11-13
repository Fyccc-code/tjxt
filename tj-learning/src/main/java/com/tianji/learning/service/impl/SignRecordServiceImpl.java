package com.tianji.learning.service.impl;


import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * @author Fyc
 * @since 2025-11-02 19:40:53
 */

@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper rabbitMqHelper;

    @Override
    public SignResultVO addSignRecords() {
        //1.签到
        //1.1.获取登录用户id
        Long userId = UserContext.getUser();
        //1.2.获取日期
        LocalDate now = LocalDate.now();
        //1.3.拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        //1.4.计算offset
        int offset = now.getDayOfMonth() - 1;
        //1.5.保存到redis true就是设置为1
        Boolean exits = redisTemplate.opsForValue().setBit(key, offset, true);
        if (BooleanUtils.isTrue(exits)) {
            //表示已经签到了
            throw new BizIllegalException("不允许重复签到");
        }
        //2.计算连续签到天数
        int signDays = countSignDays(key, now.getDayOfMonth());
        //3.计算签到积分
        int rewardPoints = 0;
        switch (signDays) {
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
        //4.保存积分明细 mq发送消息进行保存积分
        rabbitMqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1));
        //5.封装结果返回
        SignResultVO signResultVO = new SignResultVO();
        signResultVO.setSignDays(signDays);
        signResultVO.setRewardPoints(rewardPoints);
        return signResultVO;
    }

    private int countSignDays(String key, int len) {
        //1.获取本月从第一天开始到今天为止所有的所有的签到记录，得到的这个值是位的二进制对应的那个无符号十进制整数
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return 0;
        }
        //避免拆箱装箱  list里面就一个值
        int num = result.get(0).intValue();
        //2.定义计数器
        int count = 0;
        //3.循环 与1做与运算 得到最后一个bit 判断是否为0 为0终止 为1则继续
        while ((num & 1) == 1) {
            //4.计数器加1
            count++;
            //5.数字右移一位  001111->000111
            num >>>= 1;
        }
        return count;
    }

    @Override
    public Byte[] querySignRecords() {
        return new Byte[0];
    }
}
