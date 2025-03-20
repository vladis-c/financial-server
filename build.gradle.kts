plugins {
    application
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
}

group = "com.vladisc.financial.server"
version = "0.0.1"

application {
//    mainClass.set("io.ktor.server.netty.EngineMain")
    mainClass.set("com.vladisc.financial.server.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-core:3.1.0")
    implementation("io.ktor:ktor-server-netty:3.1.0")
    implementation("io.ktor:ktor-server-call-logging:3.1.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("io.ktor:ktor-server-content-negotiation:3.1.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}