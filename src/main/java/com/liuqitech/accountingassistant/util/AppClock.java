package com.liuqitech.accountingassistant.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 全应用统一业务时区。公网服务器系统时区常为 UTC，而"今天/本月/每日限流/凌晨账单"
 * 等业务语义必须按用户所在的中国时区计算，因此禁止直接使用无参的
 * LocalDate.now()/LocalDateTime.now()，一律经由本类获取。
 */
public final class AppClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private AppClock() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
