package com.eeseka.lynk

import com.eeseka.lynk.notification.infra.push_notification.FirebasePushNotificationClient
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
@ActiveProfiles("test")
class LynkApplicationTests {

    // Replaced rather than configured: the real client needs a service account key to start up,
    // and a private key is not something to keep in the repository just so a test can boot.
    @MockitoBean
    lateinit var firebasePushNotificationClient: FirebasePushNotificationClient

    @Test
    fun contextLoads() {
    }

}
