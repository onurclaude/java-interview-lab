package com.interviewlab.web.lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interactive lab endpoint'leri için okunabilir, sınırlandırılmış konsol çıktısı. Normal
 * uygulama logging'inden (INFO seviyesinde `com.interviewlab.*`) kasıtlı olarak ayrı bir
 * logger kullanır ("LAB"), böylece bir öğrenci konsolda neyin "eğitici demo çıktısı" neyin
 * "normal uygulama logu" olduğunu tek bakışta ayırt edebilir.
 */
public final class LabLog {

    private static final Logger log = LoggerFactory.getLogger("LAB");
    private static final String RULE = "=".repeat(50);

    private LabLog() {
    }

    public static void banner(String labName, String mode) {
        log.info(RULE);
        log.info("LAB: {} — {}", labName, mode);
        log.info(RULE);
    }

    public static void line(String message, Object... args) {
        log.info(message, args);
    }

    public static void lesson(String message) {
        log.info("");
        log.info("LESSON:");
        log.info(message);
        log.info(RULE);
    }
}
