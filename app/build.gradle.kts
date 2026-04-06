plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.eeseka"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(libs.spring.boot.starter.web)
//    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)

    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.spring.boot.starter.mail)

    implementation(libs.kotlin.reflect)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
}

tasks.withType<Test> {
    useJUnitPlatform()
}