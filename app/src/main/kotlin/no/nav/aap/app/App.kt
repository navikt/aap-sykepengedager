package no.nav.aap.app

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.app.kafka.Tables
import no.nav.aap.app.kafka.Topics
import no.nav.aap.app.stream.infotrygdStream
import no.nav.aap.app.stream.reproduce
import no.nav.aap.app.stream.spleisStream
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.KStreams
import no.nav.aap.kafka.streams.KStreamsConfig
import no.nav.aap.kafka.streams.KafkaStreams
import no.nav.aap.kafka.streams.extension.consume
import no.nav.aap.kafka.streams.extension.produce
import no.nav.aap.kafka.streams.store.migrateStateStore
import no.nav.aap.kafka.streams.store.scheduleMetrics
import no.nav.aap.kafka.vanilla.KafkaConfig
import no.nav.aap.ktor.config.loadConfig
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import kotlin.time.Duration.Companion.minutes

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

data class Config(val kafka: KStreamsConfig)

internal fun Application.server(kafka: KStreams = KafkaStreams) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val config = loadConfig<Config>()

    install(MicrometerMetrics) { registry = prometheus }
    install(ContentNegotiation) { jackson { registerModule(JavaTimeModule()) } }

    val sykepengedagerProducer = kafka.createProducer(KafkaConfig.copyFrom(config.kafka), Topics.sykepengedager)

    Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }
    environment.monitor.subscribe(ApplicationStopping) {
        kafka.close()
    }

    kafka.connect(
        config = config.kafka,
        registry = prometheus,
        topology = topology(prometheus, sykepengedagerProducer),
    )

    routing {
        actuator(prometheus, kafka)
    }
}

internal fun topology(
    registry: MeterRegistry,
    sykepengedagerProducer: Producer<String, SykepengedagerKafkaDto>
): Topology {
    val streams = StreamsBuilder()

    val sykepengedagerStream = streams.consume(Topics.sykepengedager)
    val sykepengedagerKTable = sykepengedagerStream
        .filter { _, value -> value?.response != null }
        .produce(Tables.sykepengedager)

    sykepengedagerKTable.scheduleMetrics(Tables.sykepengedager, 2.minutes, registry)
    sykepengedagerKTable.migrateStateStore(Tables.sykepengedager, sykepengedagerProducer)

    streams.spleisStream(sykepengedagerKTable)
    streams.infotrygdStream(sykepengedagerKTable)
    sykepengedagerStream.reproduce(sykepengedagerKTable)

    return streams.build()
}
