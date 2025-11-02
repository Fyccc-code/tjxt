package com.tianji.api.dto.remark;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Fyc
 * @since 2025-10-30 14:55:39
 */

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class LikeTimesDTO {
    private Long bizId;
    private Integer likeTimes;
}
