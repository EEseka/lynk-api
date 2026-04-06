plugins {
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.spring) apply false
	alias(libs.plugins.kotlin.jpa) apply false
	alias(libs.plugins.spring.boot) apply false
	alias(libs.plugins.spring.dependency.management) apply false
}

group = "com.eeseka"
version = "0.0.1-SNAPSHOT"

allprojects {
	group = rootProject.group
	version = rootProject.version

	repositories {
		mavenCentral()
	}

	plugins.withType<JavaPlugin> {
		extensions.configure<JavaPluginExtension> {
			toolchain {
				languageVersion.set(JavaLanguageVersion.of(21))
			}
		}
	}
}