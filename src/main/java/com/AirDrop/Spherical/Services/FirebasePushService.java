package com.AirDrop.Spherical.Services;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FirebasePushService {

    // 🟢 REMOVED 'static'
    public void wakeUpUser(String targetFcmToken, String title, String body) {
        if (targetFcmToken == null || targetFcmToken.isBlank()) return;

        try {
            Message message = Message.builder()
                    .setToken(targetFcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            FirebaseMessaging.getInstance().send(message);
            log.info("FCM Ping sent to wake up device.");
        } catch (Exception e) {
            log.warn("FCM Push Failed: {}", e.getMessage());
        }
    }
}