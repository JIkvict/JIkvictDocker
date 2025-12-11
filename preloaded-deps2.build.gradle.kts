plugins {
    kotlin("jvm") version "2.1.21"
    `java-library`
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

kotlin {
    jvmToolchain(21)
}


dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
        implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.20")
    }
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1")
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.1"))

    implementation("org.jetbrains:annotations:13.0")
    implementation("org.jetbrains:annotations:23.0.0")

    implementation("org.mockito:mockito-core:5.20.0")
    implementation("org.mockito.kotlin:mockito-kotlin:5.3.1")

    implementation("net.bytebuddy:byte-buddy:1.17.7")
    implementation("net.bytebuddy:byte-buddy-agent:1.17.7")

    implementation("org.objenesis:objenesis:3.3")

    implementation("org.assertj:assertj-core:3.27.6")

    implementation("io.mockk:mockk:1.14.6")
    implementation("io.mockk:mockk-jvm:1.14.6")
    implementation("io.mockk:mockk-dsl-jvm:1.14.6")
    implementation("io.mockk:mockk-agent-jvm:1.14.6")
    implementation("io.mockk:mockk-agent-api-jvm:1.14.6")
    implementation("io.mockk:mockk-core-jvm:1.14.6")

    implementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    implementation(platform("org.junit:junit-bom:5.10.1"))

    implementation("org.junit.jupiter:junit-jupiter:5.12.2")
    implementation("org.junit.jupiter:junit-jupiter-api:5.12.2")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.12.2")
    implementation("org.junit.jupiter:junit-jupiter-params:5.12.2")

    implementation("org.junit.platform:junit-platform-commons:1.12.2")
    implementation("org.junit.platform:junit-platform-engine:1.12.2")
    implementation("org.junit.platform:junit-platform-launcher:1.12.2")

    implementation(platform("org.junit:junit-bom:5.12.2"))

    implementation("junit:junit:4.13.2")
    implementation("org.hamcrest:hamcrest-core:1.3")
    implementation("org.hamcrest:hamcrest-parent:1.3")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.0")

    implementation("com.github.JIkvict.JIkvictTestingLibrary:JIkvictTestingPlugin:v0.0.9")
}

tasks.register("downloadDependencies") {
    doLast {
        configurations.forEach { canBeResolved ->
            if (canBeResolved.isCanBeResolved) {
                try { canBeResolved.resolve() } catch (e: Exception) {}
            }
        }
    }
}