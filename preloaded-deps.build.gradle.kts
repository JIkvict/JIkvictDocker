plugins {
    kotlin("jvm") version "2.2.0"

    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("org.junit.platform:junit-platform-launcher:1.10.1")
    implementation("org.junit.platform:junit-platform-engine:1.10.1")
    implementation("org.junit.platform:junit-platform-commons:1.10.1")

    implementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    implementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    implementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    implementation("org.junit.jupiter:junit-jupiter:5.10.1")

    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("com.h2database:h2:2.2.224")
    implementation("org.reflections:reflections:0.10.2")

    implementation("org.assertj:assertj-core:3.27.6")
    implementation("org.mockito:mockito-core:5.20.0")
    implementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    implementation("io.mockk:mockk:1.14.6")

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(kotlin("test"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.0")

    implementation("com.github.JIkvict.JIkvictTestingLibrary:JIkvictTestingLibrary:v0.0.10")

    implementation("com.github.JIkvict.JIkvictTestingLibrary:JIkvictTestingPlugin:v0.0.10")
}
kotlin {
    jvmToolchain(21)
}

tasks.register("downloadDependencies") {
    doLast {
        configurations.forEach { config ->
            if (config.isCanBeResolved) {
                try {
                    config.resolve()
                } catch (e: Exception) {
                    println("Note: Could not resolve configuration ${config.name} (this is normal for some internal configs)")
                }
            }
        }
    }
}