package com.AirDrop.Spherical.Services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private final StringRedisTemplate redisTemplate;

    private static final String REPORT_COUNT_PREFIX = "mod:reports:count:";
    private static final String REPORT_LOG_PREFIX = "mod:reports:users:";
    private static final String BLOCKED_USERS_KEY = "mod:blocked:users";
    private static final String EXCEL_FILE_PATH = "blocked_users_audit.xlsx";


    private static final List<String> PROFANITY_FILTER = Arrays.asList(
            "abuse", "harass", "bitch", "bastard", "fuck", "shit", "idiot", "stupid"
    );

    /**
     * Reports a user. Increments counter in Redis. When count reaches 5, blocks user and logs to Excel.
     */
    public boolean reportUser(String reporter, String reportedUsername) {
        String target = reportedUsername.trim().toLowerCase();
        String reporterUser = reporter.trim().toLowerCase();

        if (target.equals(reporterUser)) {
            throw new IllegalArgumentException("Cannot report yourself.");
        }

        // Prevent duplicate reporting by the same reporter
        String logKey = REPORT_LOG_PREFIX + target;
        Boolean alreadyReported = redisTemplate.opsForSet().isMember(logKey, reporterUser);
        if (Boolean.TRUE.equals(alreadyReported)) {
            return false; // Already reported
        }

        redisTemplate.opsForSet().add(logKey, reporterUser);
        Long reportCount = redisTemplate.opsForValue().increment(REPORT_COUNT_PREFIX + target);

        log.warn("🚨 [MODERATION] @{} reported @{}. Total reports: {}", reporterUser, target, reportCount);

        if (reportCount != null && reportCount >= 5) {
            blockUserAndExportToExcel(target, "Exceeded 5 Abuse/Harassment Reports");
        }

        return true;
    }

    /**
     * Checks if a message contains bad words or profanity.
     */
    public boolean containsBadWords(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase();
        for (String word : PROFANITY_FILTER) {
            if (lower.contains(word)) return true;
        }
        return false;
    }

    /**
     * Checks if a user is blocked.
     */
    public boolean isUserBlocked(String username) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(BLOCKED_USERS_KEY, username.trim().toLowerCase()));
    }

    /**
     * Blocks user in Redis and appends entry to blocked_users_audit.xlsx
     */
    private synchronized void blockUserAndExportToExcel(String username, String reason) {
        redisTemplate.opsForSet().add(BLOCKED_USERS_KEY, username);
        log.error("⛔ [AUTO-BLOCK] @{} has been permanently blocked! Exporting to Excel...", username);

        Workbook workbook;
        File file = new File(EXCEL_FILE_PATH);

        try {
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                workbook = new XSSFWorkbook(fis);
                fis.close();
            } else {
                workbook = new XSSFWorkbook();
                Sheet sheet = workbook.createSheet("Blocked Users");
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("Username");
                header.createCell(1).setCellValue("Reason");
                header.createCell(2).setCellValue("Blocked At");
            }

            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            Row newRow = sheet.createRow(lastRow + 1);

            newRow.createCell(0).setCellValue(username);
            newRow.createCell(1).setCellValue(reason);
            newRow.createCell(2).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            FileOutputStream fos = new FileOutputStream(EXCEL_FILE_PATH);
            workbook.write(fos);
            fos.close();
            workbook.close();

            log.info("📊 [EXCEL SUCCESS] Appended @{} to {}", username, EXCEL_FILE_PATH);

        } catch (IOException e) {
            log.error("❌ [EXCEL ERROR] Failed to write to Excel audit file:", e);
        }
    }
}