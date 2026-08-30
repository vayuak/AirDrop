package com.AirDrop.Spherical.DTOs;

import lombok.Data;

@Data
public class LocationPingRequest {
    private double latitude;
    private double longitude;
    private String statusMessage; // e.g., "Looking for a coffee buddy ☕"
}