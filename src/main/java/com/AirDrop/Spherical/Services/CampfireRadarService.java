package com.AirDrop.Spherical.Services;

import com.AirDrop.Spherical.Clients.UserCatalogClient;
import com.AirDrop.Spherical.DTOs.NearbyPeerResponse;
import com.AirDrop.Spherical.Utilities.CompressionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampfireRadarService {

    private final StringRedisTemplate redisTemplate;
    private final AIModerationService aiModerationService;
    private final UserCatalogClient userCatalogClient;
    private final FirebasePushService firebasePushService;

    private static final String GEO_KEY = "campfire:radar:nodes";
    private static final String GEO_DROPS_KEY = "campfire:radar:drops";
    private static final String DROP_PREFIX = "campfire:drop:";
    private static final String LAST_PING_ZSET = "campfire:radar:timestamps";

    private static final String REPORT_COUNT_PREFIX = "campfire:mod:reports:count:";
    private static final String REPORT_SET_PREFIX = "campfire:mod:reports:users:";

    private static final double PRIMARY_RADAR_RADIUS_METERS = 100.0;
    private static final double BUFFER_RADIUS_METERS = 127.0;

    public void postDrop(String username, double lat, double lng, String message, boolean isPremium) {
        String cleanUser = username.trim().toLowerCase();

        if (message != null && message.length() > 150) {
            throw new IllegalArgumentException("Message too long (max 150 characters).");
        }
        String blocked = com.AirDrop.Spherical.Utilities.PromoFilter.check(message);
        if (blocked != null) {
            throw new IllegalArgumentException(blocked);   // rejected BEFORE it counts against the daily limit or goes live
        }

        if (!isPremium) {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String limitKey = "campfire:limit:" + today + ":" + cleanUser;

            String currentCountStr = redisTemplate.opsForValue().get(limitKey);
            if (currentCountStr != null && Integer.parseInt(currentCountStr) >= 10) {
                throw new IllegalStateException("Daily AirDrop limit reached (10/10). Resets at midnight!");
            }

            Long newCount = redisTemplate.opsForValue().increment(limitKey);
            if (newCount != null && newCount == 1) {
                LocalDateTime midnight = LocalDate.now().plusDays(1).atStartOfDay();
                redisTemplate.expire(limitKey, Duration.between(LocalDateTime.now(), midnight));
            }
        }

        if (message != null && !message.isBlank()) {
            String dropId = UUID.randomUUID().toString();
            String rawMessage = message.trim();

            String compressedMsg = CompressionUtil.compress(rawMessage);
            String payload = cleanUser + "|||" + compressedMsg;

            redisTemplate.opsForGeo().add(GEO_DROPS_KEY, new Point(lng, lat), dropId);
            redisTemplate.opsForValue().set(DROP_PREFIX + dropId, payload, Duration.ofMinutes(10));

            log.info("🔥 [DROP INSTANT] @{} posted a compressed drop. DropID: {}", cleanUser, dropId);
            verifyContentAsync(dropId, cleanUser, rawMessage);
        }
    }

    @Async
    protected void verifyContentAsync(String dropId, String username, String rawMessage) {
        if (!aiModerationService.isMessageSafe(rawMessage)) {
            redisTemplate.opsForGeo().remove(GEO_DROPS_KEY, dropId);
            redisTemplate.opsForValue().getAndDelete(DROP_PREFIX + dropId);
            log.warn("🛡️ [AI REMOVED] Toxic drop from @{} removed post-publish.", username);
            reportUser("SYSTEM_AI", username);
        }
    }

    public List<NearbyPeerResponse> scanNearbyPeers(String username, double lat, double lng) {
        Point center = new Point(lng, lat);
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                GEO_DROPS_KEY, GeoReference.fromCoordinate(center),
                new Distance(BUFFER_RADIUS_METERS, RedisGeoCommands.DistanceUnit.METERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending()
        );

        List<NearbyPeerResponse> nearbyPeers = new ArrayList<>();
        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
                String dropId = result.getContent().getName();
                double distanceMeters = result.getDistance().getValue();
                if (distanceMeters > PRIMARY_RADAR_RADIUS_METERS) continue;

                String payload = redisTemplate.opsForValue().get(DROP_PREFIX + dropId);
                if (payload != null && payload.contains("|||")) {
                    String[] parts = payload.split("\\|\\|\\|", 2);
                    String peerUsername = parts[0];
                    String compressedMsg = parts[1];

                    String rawMsg = CompressionUtil.decompress(compressedMsg);
                    nearbyPeers.add(new NearbyPeerResponse(peerUsername, Math.round(distanceMeters * 10.0) / 10.0, rawMsg));
                } else {
                    redisTemplate.opsForZSet().remove(GEO_DROPS_KEY, dropId);
                }
            }
        }
        return nearbyPeers;
    }

    public void updateUserLocation(String username, double lat, double lng) {
        String cleanUser = username.trim().toLowerCase();
        redisTemplate.opsForGeo().add(GEO_KEY, new Point(lng, lat), cleanUser);
        redisTemplate.opsForZSet().add(LAST_PING_ZSET, cleanUser, System.currentTimeMillis());
    }

    @Scheduled(fixedRate = 900000)
    public void cleanupGhostLocations() {
        long thirtyMinutesAgo = System.currentTimeMillis() - (30L * 60L * 1000L);
        var staleUsers = redisTemplate.opsForZSet().rangeByScore(LAST_PING_ZSET, 0, thirtyMinutesAgo);

        if (staleUsers != null && !staleUsers.isEmpty()) {
            String[] staleUsersArray = staleUsers.toArray(new String[0]);
            redisTemplate.opsForGeo().remove(GEO_KEY, staleUsersArray);
            redisTemplate.opsForZSet().remove(LAST_PING_ZSET, (Object[]) staleUsersArray);
            log.info("🧹 [CLEANUP] Removed {} inactive users from the radar map.", staleUsers.size());
        }
    }

    public boolean reportUser(String reporter, String reportedUsername) {
        String target = reportedUsername.trim().toLowerCase();
        String reporterUser = reporter.trim().toLowerCase();
        if (target.equals(reporterUser)) return false;

        String reportSetKey = REPORT_SET_PREFIX + target;
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(reportSetKey, reporterUser))) return false;

        redisTemplate.opsForSet().add(reportSetKey, reporterUser);
        Long reportCount = redisTemplate.opsForValue().increment(REPORT_COUNT_PREFIX + target);

        if (reportCount != null) {
            if (reportCount == 1 || reportCount == 2) {
                sendSystemWarning(target, reportCount);
            } else if (reportCount >= 3) {
                sendSystemWarning(target, reportCount);
                blockUserAndSyncPostgres(target);
            }
        }
        return true;
    }

    @Async
    protected void sendSystemWarning(String username, Long strikeCount) {
        String warningMsg;

        if (strikeCount >= 3) {
            warningMsg = "SYSTEM ALERT: You have received 3 strikes for violating community guidelines. You are permanently blocked from this platform.";
        } else {
            warningMsg = "SYSTEM ALERT: Your account has been reported. This is strike " + strikeCount + " of 3. Further violations will result in a permanent ban.";
        }

        String dropId = UUID.randomUUID().toString();
        String payload = "system|||" + CompressionUtil.compress(warningMsg);
        log.warn("⚠️ [STRIKE {}] System warning issued to @{}: {}", strikeCount, username, warningMsg);
    }

    private void blockUserAndSyncPostgres(String username) {
        redisTemplate.opsForGeo().remove(GEO_KEY, username);
        redisTemplate.opsForZSet().remove(LAST_PING_ZSET, username);
        redisTemplate.delete(REPORT_SET_PREFIX + username);
        redisTemplate.delete(REPORT_COUNT_PREFIX + username);

        log.error("⛔ [STRIKE LIMIT HIT] @{} removed from map. Triggering permanent Auth ban...", username);

        try {
            userCatalogClient.blockInternalUser(username);
            log.info("📊 [DB SYNC SUCCESS] Banned @{} in UserCatalog.", username);
        } catch (Exception e) {
            log.error("❌ [DB SYNC ERROR] Failed to reach UserCatalog: {}", e.getMessage());
        }
    }

    @Async
    public void notifyScannedPeers(List<NearbyPeerResponse> nearbyUsers, String scannerUsername) {
        for (NearbyPeerResponse peer : nearbyUsers) {
            try {
                String token = userCatalogClient.fetchTokenFromUserCatalog(peer.getUsername());

                if (token != null && !token.isBlank()) {
                    // 🟢 USE THE INSTANCE VARIABLE (lowercase `firebasePushService`)
                    firebasePushService.wakeUpUser(
                            token,
                            "Campfire Radar",
                            "📍 " + scannerUsername + " is scanning nearby. Drop a ping to connect!"
                    );
                }
            } catch (Exception e) {
                // Ignore missing tokens or timeouts
            }
        }
    }
}