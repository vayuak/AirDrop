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
    private final UserCatalogClient userCatalogClient; // 🛡️ Replaced RestTemplate with secure Feign Client

    // Geo-Spatial & Moderation Keys
    private static final String GEO_KEY = "campfire:radar:nodes";
    private static final String GEO_DROPS_KEY = "campfire:radar:drops";
    private static final String DROP_PREFIX = "campfire:drop:";
    private static final String LAST_PING_ZSET = "campfire:radar:timestamps";

    private static final String REPORT_COUNT_PREFIX = "campfire:mod:reports:count:";
    private static final String REPORT_SET_PREFIX = "campfire:mod:reports:users:";
    public static final String BLOCKED_SET_KEY = "campfire:mod:blocked:users";

    private static final double PRIMARY_RADAR_RADIUS_METERS = 50.0;
    private static final double BUFFER_RADIUS_METERS = 60.0;

    /**
     * POST DROP: Compresses text, instant response for 2G, AI runs in the background.
     */
    public void postDrop(String username, double lat, double lng, String message, boolean isPremium) {
        String cleanUser = username.trim().toLowerCase();

        if (isUserBlocked(cleanUser)) {
            throw new IllegalStateException("Your account is blocked.");
        }

        // 10-Drop Daily Limit for Free Users
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

        // Instant Post with Compression
        if (message != null && !message.isBlank()) {
            String dropId = UUID.randomUUID().toString();
            String rawMessage = message.trim();

            // 🗜️ COMPRESS: Shrink the text before it hits Redis
            String compressedMsg = CompressionUtil.compress(rawMessage);
            String payload = cleanUser + "|||" + compressedMsg;

            redisTemplate.opsForGeo().add(GEO_DROPS_KEY, new Point(lng, lat), dropId);
            redisTemplate.opsForValue().set(DROP_PREFIX + dropId, payload, Duration.ofMinutes(10));

            log.info("🔥 [DROP INSTANT] @{} posted a compressed drop. DropID: {}", cleanUser, dropId);

            // 🤖 AI CHECK: We pass the RAW message to the AI so it can actually read it
            verifyContentAsync(dropId, cleanUser, rawMessage);
        }
    }

    /**
     * ASYNC AI MODERATION
     */
    @Async
    protected void verifyContentAsync(String dropId, String username, String rawMessage) {
        if (!aiModerationService.isMessageSafe(rawMessage)) {
            redisTemplate.opsForGeo().remove(GEO_DROPS_KEY, dropId);
            redisTemplate.opsForValue().getAndDelete(DROP_PREFIX + dropId);
            log.warn("🛡️ [AI REMOVED] Toxic drop from @{} removed post-publish.", username);
            reportUser("SYSTEM_AI", username);
        }
    }

    /**
     * RADAR SCAN: Decompresses payloads on the fly
     */
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

                    if (!isUserBlocked(peerUsername)) {
                        // 🔓 DECOMPRESS: Restore the text before sending to the phone
                        String rawMsg = CompressionUtil.decompress(compressedMsg);
                        nearbyPeers.add(new NearbyPeerResponse(peerUsername, Math.round(distanceMeters * 10.0) / 10.0, rawMsg));
                    }
                } else {
                    redisTemplate.opsForZSet().remove(GEO_DROPS_KEY, dropId);
                }
            }
        }
        return nearbyPeers;
    }

    /**
     * UPDATE LOCATION
     */
    public void updateUserLocation(String username, double lat, double lng) {
        String cleanUser = username.trim().toLowerCase();
        if (!isUserBlocked(cleanUser)) {
            redisTemplate.opsForGeo().add(GEO_KEY, new Point(lng, lat), cleanUser);
            redisTemplate.opsForZSet().add(LAST_PING_ZSET, cleanUser, System.currentTimeMillis());
        }
    }

    /**
     * SCHEDULED CLEANUP
     */
    @Scheduled(fixedRate = 900000)
    public void cleanupGhostLocations() {
        long thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000);
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

        if (reportCount != null && reportCount >= 3) {
            blockUserAndSyncPostgres(target);
        }
        return true;
    }

    public boolean isUserBlocked(String username) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(BLOCKED_SET_KEY, username.trim().toLowerCase()));
    }

    /**
     * POSTGRES SYNC: Using strict internal Feign Client
     */
    private void blockUserAndSyncPostgres(String username) {
        redisTemplate.opsForSet().add(BLOCKED_SET_KEY, username);
        redisTemplate.opsForGeo().remove(GEO_KEY, username);
        redisTemplate.opsForGeo().remove(GEO_DROPS_KEY, username);

        log.error("⛔ [AUTO-BLOCK] @{} blocked. Syncing to UserCatalog Postgres...", username);

        try {
            // 🛡️ FAST REST CALL via OpenFeign
            userCatalogClient.blockInternalUser(username);
            log.info("📊 [DB SYNC SUCCESS] Permanently banned @{} in Postgres.", username);
        } catch (Exception e) {
            log.error("❌ [DB SYNC ERROR] Failed to reach UserCatalog: {}", e.getMessage());
        }
    }
}