plugins {
    id("java-library")
    id("org.allaymc.gradle.plugin") version "0.2.1"
}

group = "org.allaymc.skyblock"
description = "Vibe-Skyblock game-mode plugin for AllayMC"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// See also https://github.com/AllayMC/AllayGradle
allay {
    api = "0.27.0"

    plugin {
        entrance = ".SkyblockPlugin"
        authors += "AI"
        website = "https://github.com/skyblock"
    }
}

dependencies {
    compileOnly(group = "org.projectlombok", name = "lombok", version = "1.18.34")
    annotationProcessor(group = "org.projectlombok", name = "lombok", version = "1.18.34")

    implementation(group = "com.google.code.gson", name = "gson", version = "2.10.1")

    testImplementation(group = "net.jqwik", name = "jqwik", version = "1.8.4")
    testImplementation(group = "org.mockito", name = "mockito-core", version = "5.11.0")
    testImplementation(group = "org.allaymc.allay", name = "api", version = "0.27.0")
    testRuntimeOnly(group = "org.junit.platform", name = "junit-platform-launcher", version = "1.10.2")
}

tasks.test {
    useJUnitPlatform()
}
