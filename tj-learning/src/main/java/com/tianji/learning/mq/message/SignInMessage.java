package com.tianji.learning.mq.message;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Fyc
 * @since 2025-11-02 20:05:03
 */

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class SignInMessage {
    private Long userId;
    private Integer points;

    /*//静态工厂方法
    public static SignInMessage of(Long userId, Integer points) {
        return new SignInMessage(userId, points);
    }*/

}
