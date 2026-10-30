plugins {
    java
    id("org.springframework.boot") version "3.4.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.openjfx.javafxplugin") version "0.1.0" apply false
}

allprojects {
    group = "com.riseup"
    version = "0.2.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            // Local JDK 23 is available; CI uses Temurin 21 via setup-java.
            languageVersion.set(JavaLanguageVersion.of(23))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
