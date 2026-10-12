plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.dabblepoint"
version = "0.0.1-SNAPSHOT"
description = "travel-agent"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.1")
    }
}

dependencies {
    implementation("org.springframework.kafka:spring-kafka")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.springframework.ai:spring-ai-vector-store-advisor")

    implementation("org.springframework.ai:spring-ai-vector-store")

    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    implementation("org.springframework.ai:spring-ai-starter-model-ollama")

    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // Web starter (includes Tomcat server + REST framework)
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Kotlin JSON support
    implementation("tools.jackson.module:jackson-module-kotlin")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
