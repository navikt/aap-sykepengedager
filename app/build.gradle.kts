val aapLibVersion = "3.4.0"
val ktorVersion = "2.1.2"

plugins {
    id("io.ktor.plugin")
    application
}

application {
    mainClass.set("no.nav.aap.app.AppKt")
}

dependencies {
    implementation(project(":dto-kafka"))
    implementation("com.github.navikt.aap-libs:ktor-utils:$aapLibVersion")
    implementation("com.github.navikt.aap-libs:kafka:$aapLibVersion")
    testImplementation("com.github.navikt.aap-libs:kafka-test:$aapLibVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")

    implementation("io.micrometer:micrometer-registry-prometheus:1.9.4")

    implementation("ch.qos.logback:logback-classic:1.4.3")
    implementation("net.logstash.logback:logstash-logback-encoder:7.2")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.4")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.4")

    testImplementation(kotlin("test"))

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.0.1")
}
