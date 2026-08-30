package com.AirDrop.Spherical.Controllers;

import com.AirDrop.Spherical.DTOs.LocationPingRequest;
import com.AirDrop.Spherical.DTOs.NearbyPeerResponse;
import com.AirDrop.Spherical.Services.CampfireRadarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/campfire")
@RequiredArgsConstructor
public class CampfireRadarController {

    private final CampfireRadarService campfireService;

    @PostMapping("/drop")
    public ResponseEntity<?> postDrop(
            @RequestBody LocationPingRequest request,
            @RequestHeader(value = "X-Is-Premium", defaultValue = "false") boolean isPremium,
            Principal principal) {

        if (principal == null) return ResponseEntity.status(401).build();

        try {
            campfireService.postDrop(
                    principal.getName(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getStatusMessage(),
                    isPremium
            );
            return ResponseEntity.ok(Map.of("message", "Drop posted to Campfire!"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/report/{targetUsername}")
    public ResponseEntity<?> reportUser(@PathVariable String targetUsername, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        boolean success = campfireService.reportUser(principal.getName(), targetUsername);
        if (!success) {
            return ResponseEntity.badRequest().body(Map.of("message", "You have already reported this user."));
        }

        return ResponseEntity.ok(Map.of("message", "Report submitted."));
    }

    @GetMapping("/scan")
    public ResponseEntity<List<NearbyPeerResponse>> scanRadar(
            @RequestParam double lat,
            @RequestParam double lng,
            Principal principal) {

        if (principal == null) return ResponseEntity.status(401).build();

        List<NearbyPeerResponse> peers = campfireService.scanNearbyPeers(principal.getName(), lat, lng);
        return ResponseEntity.ok(peers);
    }

}