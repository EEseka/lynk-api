import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.eeseka"
version = "0.0.1-SNAPSHOT"

tasks {
    named<BootJar>("bootJar") {
        from(project(":notification").projectDir.resolve("src/main/resources")) {
            into("")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":user"))
    implementation(project(":spot"))
    implementation(project(":hangout"))
    implementation(project(":lobby"))
    implementation(project(":payment"))
    implementation(project(":notification"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)

    implementation(libs.spring.boot.starter.actuator)

    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    implementation(libs.spring.boot.starter.data.redis)

    implementation(libs.spring.boot.flyway)
    runtimeOnly(libs.flyway.database.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.spring.boot.starter.amqp)
    testImplementation(libs.spring.boot.starter.mail)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}