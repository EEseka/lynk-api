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

    implementation(libs.spring.boot.starter.validation)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)

    implementation(libs.spring.boot.starter.amqp)

    runtimeOnly(libs.postgresql)
}