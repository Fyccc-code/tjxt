package com.tianji.learning.service;

import com.tianji.learning.domain.vo.SignResultVO;

/**
 * @author Fyc
 * @since 2025-11-02 19:37:19
 */
public interface ISignRecordService {
    SignResultVO addSignRecords();

    Byte[] querySignRecords();
}
