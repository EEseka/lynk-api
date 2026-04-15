plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    id("java-library")
}

group = "com.eeseka"
version = "unspecified"

dependencies {
    api(libs.kotlin.reflect)
    api(libs.jackson.module.kotlin)

    // --- Spring Boot Core ---
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)

    implementation(libs.spring.boot.starter.amqp)

    // --- JWT ---
    implementation(libs.jwt.api)
    runtimeOnly(libs.jwt.impl)
    runtimeOnly(libs.jwt.jackson)
}