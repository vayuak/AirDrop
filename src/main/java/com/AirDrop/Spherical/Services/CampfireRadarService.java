package com.AirDrop.Spherical.Services;

import com.AirDrop.Spherical.DTOs.NearbyPeerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;

import org.springframework.data.redis.domain.geo.GeoReference;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampfireRadarService {

    private final StringRedisTemplate redisTemplate;
    private final AIModerationService aiModerationService;

    private static final String GEO_KEY = "campfire:radar:nodes";
    private static final String DROP_PREFIX = "campfire:drop:";
    private static final String MSG_LIMIT_PREFIX = "campfire:limit:24h:";
    private static final String REPORT_COUNT_PREFIX = "campfire:mod:reports:count:";
    private static final String REPORT_SET_PREFIX = "campfire:mod:reports:users:";
    public static final String BLOCKED_SET_KEY = "campfire:mod:blocked:users";
    private static final String EXCEL_FILE_PATH = "blocked_users_audit.xlsx";

    private static final double PRIMARY_RADAR_RADIUS_METERS = 50.0;
    private static final double BUFFER_RADIUS_METERS = 60.0;

    public void postDrop(String username, double lat, double lng, String message, boolean isPremium) {
        String cleanUser = username.trim().toLowerCase();

        if (isUserBlocked(cleanUser)) {
            throw new IllegalStateException("Your account has been blocked due to community policy violations.");
        }

        if (!aiModerationService.isMessageSafe(message)) {
            throw new IllegalArgumentException("Drop rejected: Content violates global safety guidelines (Abuse/Harassment/Profanity).");
        }

        if (!isPremium) {
            String limitKey = MSG_LIMIT_PREFIX + cleanUser;
            String currentCountStr = redisTemplate.opsForValue().get(limitKey);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;

            if (currentCount >= 5) {
                throw new IllegalStateException("24-hour Drop limit reached (5/5). Upgrade to Premium for unlimited drops!");
            }

            Long newCount = redisTemplate.opsForValue().increment(limitKey);
            if (newCount != null && newCount == 1) {
                redisTemplate.expire(limitKey, Duration.ofHours(24));
            }
        }

        redisTemplate.opsForGeo().add(GEO_KEY, new Point(lng, lat), cleanUser);

        if (message != null && !message.isBlank()) {
            redisTemplate.opsForValue().set(
                    DROP_PREFIX + cleanUser,
                    message.trim(),
                    Duration.ofMinutes(10)
            );
        }

        log.info("🔥 [CAMPFIRE DROP SUCCESS] @{} posted a drop. (Premium: {})", cleanUser, isPremium);
    }

    public List<NearbyPeerResponse> scanNearbyPeers(String username, double lat, double lng) {
        String activeUser = (username != null) ? username.trim().toLowerCase() : "";
        Point center = new Point(lng, lat);

        RedisGeoCommands.GeoSearchCommandArgs args = RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                .includeDistance()
                .sortAscending();

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(center),
                new Distance(BUFFER_RADIUS_METERS, RedisGeoCommands.DistanceUnit.METERS),
                args
        );

        List<NearbyPeerResponse> nearbyPeers = new ArrayList<>();

        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
                String peerName = result.getContent().getName();

                if (isUserBlocked(peerName)) continue;

                double distanceMeters = result.getDistance().getValue();

                if (distanceMeters > (PRIMARY_RADAR_RADIUS_METERS + 1.0)) {
                    continue;
                }

                String dropMsg = redisTemplate.opsForValue().get(DROP_PREFIX + peerName);

                if (dropMsg != null && !dropMsg.isBlank()) {
                    nearbyPeers.add(new NearbyPeerResponse(
                            peerName,
                            Math.round(distanceMeters * 10.0) / 10.0,
                            dropMsg
                    ));
                } else {
                    redisTemplate.opsForZSet().remove(GEO_KEY, peerName);
                }
            }
        }
        return nearbyPeers;
    }

    public boolean reportUser(String reporter, String reportedUsername) {
        String target = reportedUsername.trim().toLowerCase();
        String reporterUser = reporter.trim().toLowerCase();

        if (target.equals(reporterUser)) {
            throw new IllegalArgumentException("You cannot report yourself.");
        }

        String reportSetKey = REPORT_SET_PREFIX + target;
        Boolean alreadyReported = redisTemplate.opsForSet().isMember(reportSetKey, reporterUser);
        if (Boolean.TRUE.equals(alreadyReported)) {
            return false;
        }

        redisTemplate.opsForSet().add(reportSetKey, reporterUser);
        Long reportCount = redisTemplate.opsForValue().increment(REPORT_COUNT_PREFIX + target);

        log.warn("🚨 [CAMPFIRE REPORT] @{} reported @{}. Total reports: {}", reporterUser, target, reportCount);

        if (reportCount != null && reportCount >= 5) {
            blockUserAndExportToExcel(target, "Automated Block: Reached 5 Community Reports");
        }

        return true;
    }

    public boolean isUserBlocked(String username) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(BLOCKED_SET_KEY, username.trim().toLowerCase()));
    }

    public void updateUserLocation(String username, double lat, double lng) {
        String cleanUser = username.trim().toLowerCase();
        if (!isUserBlocked(cleanUser)) {
            redisTemplate.opsForGeo().add(GEO_KEY, new Point(lng, lat), cleanUser);
        }
    }

    private synchronized void blockUserAndExportToExcel(String username, String reason) {
        redisTemplate.opsForSet().add(BLOCKED_SET_KEY, username);
        redisTemplate.opsForGeo().remove(GEO_KEY, username);
        redisTemplate.delete(DROP_PREFIX + username);

        log.error("⛔ [AUTO-BLOCK TRIGGERED] @{} blocked. Logging to Excel...", username);

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

            log.info("📊 [EXCEL AUDIT SUCCESS] Appended @{} to {}", username, EXCEL_FILE_PATH);

        } catch (IOException e) {
            log.error("❌ [EXCEL ERROR] Failed writing to Excel file:", e);
        }
    }
}