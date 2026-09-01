package com.eeseka.lynk.support

import com.eeseka.lynk.notification.infra.email.BrevoEmailClient
import com.eeseka.lynk.notification.infra.push_notification.FirebasePushNotificationClient
import com.eeseka.lynk.payment.infra.bank_logo.BankLogoClient
import com.eeseka.lynk.payment.infra.paystack.PaystackClient
import com.eeseka.lynk.spot.infra.google_places.GooglePlacesClient
import com.eeseka.lynk.user.infra.storage.SupabaseUserStorageClient
import com.eeseka.lynk.user.service.GoogleAuthService
import org.junit.jupiter.api.AfterEach
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    TestFixtures::class,
    TestAccounts::class,
    TestHangouts::class,
    TestBroker::class,
    DatabaseCleaner::class
)
abstract class IntegrationTest {

    companion object {
        /**
         * Declared static so the container starts once and is shared by every test class, and left
         * for Spring Boot to start: `@ServiceConnection` both manages its lifecycle and hands the
         * context the url and credentials, so none are written in `application-test.yaml`.
         *
         * The tag tracks Supabase, which runs Postgres 17.
         */
        @JvmStatic
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @Autowired
    protected lateinit var fixtures: TestFixtures

    @Autowired
    protected lateinit var accounts: TestAccounts

    @Autowired
    protected lateinit var hangouts: TestHangouts

    @Autowired
    protected lateinit var broker: TestBroker

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @MockitoBean
    protected lateinit var paystackClient: PaystackClient

    @MockitoBean
    protected lateinit var googlePlacesClient: GooglePlacesClient

    @MockitoBean
    protected lateinit var bankLogoClient: BankLogoClient

    @MockitoBean
    protected lateinit var supabaseUserStorageClient: SupabaseUserStorageClient

    // Not a client by name, but it is still a trip to Google: it checks a token against Google's
    // signing certificates. Tests say what a token means instead.
    @MockitoBean
    protected lateinit var googleAuthService: GoogleAuthService

    @MockitoBean
    protected lateinit var firebasePushNotificationClient: FirebasePushNotificationClient

    @MockitoBean
    protected lateinit var brevoEmailClient: BrevoEmailClient

    /**
     * The broker is external too. Mocked rather than run, both so a test never waits on a connection
     * that is not there and so a test can ask what the application tried to publish.
     */
    @MockitoBean
    protected lateinit var rabbitTemplate: RabbitTemplate

    @AfterEach
    fun clearDatabase() {
        databaseCleaner.clear()
        broker.reset()
    }
}