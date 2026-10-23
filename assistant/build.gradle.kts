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
    implementation(project(":spot"))
    implementation(project(":hangout"))
    implementation(project(":lobby"))

    implementation(libs.spring.boot.starter.validation)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)

    implementation(libs.spring.boot.starter.data.redis)

    implementation(libs.spring.boot.starter.amqp)

    implementation(libs.koog.agents)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
}