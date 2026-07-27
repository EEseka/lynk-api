plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    id("java-library")
}

group = "com.eeseka"
version = "unspecified"

dependencies {
    implementation(project(":common"))
    implementation(project(":hangout"))
    implementation(project(":spot"))

    implementation(libs.spring.boot.starter.web)

    implementation(libs.spring.boot.starter.websocket)

    // Only for @TransactionalEventListener (spring-tx); the lobby persists nothing itself.
    // Same reason for the inclusion of the jpa plugin
    implementation(libs.spring.boot.starter.data.jpa)
}