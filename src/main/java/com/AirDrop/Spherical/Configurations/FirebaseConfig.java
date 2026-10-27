package com.AirDrop.Spherical.Configurations;


import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @PostConstruct
    public void initializeFirebase() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) return;

            String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            InputStream in;
            if (path != null && new java.io.File(path).exists()) {
                in = new FileInputStream(path);
            } else {
                in = getClass().getClassLoader().getResourceAsStream("firebase-key.json");
            }
            if (in == null) {
                log.warn("Firebase key not found - push notifications are DISABLED (chat still works while the app is open).");
                return;
            }
            try (in) {
                FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(in)).build());
            }
            log.info("Firebase Admin SDK initialised.");
        } catch (Exception e) {
            log.error("Failed to initialise Firebase Admin SDK", e);
        }
    }
}
