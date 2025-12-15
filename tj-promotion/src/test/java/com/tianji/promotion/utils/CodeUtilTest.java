package com.tianji.promotion.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Fyc
 * @since 2025-11-19 22:07:55
 */

class CodeUtilTest {

    @Test
    void generateCode() {
        String code = CodeUtil.generateCode(1, 1000);
    }

    @Test
    void parseCode() {
    }
}