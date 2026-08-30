package com.AirDrop.Spherical.DTOs;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NearbyPeerResponse {
    private String username;
    private double distanceInMeters;
    private String statusMessage;
}