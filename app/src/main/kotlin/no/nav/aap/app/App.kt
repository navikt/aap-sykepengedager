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
import no.nav.aap.app.stream.spleisStream
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.v2.Streams
import no.nav.aap.kafka.streams.v2.KafkaStreams
import no.nav.aap.kafka.streams.v2.Topology
import no.nav.aap.kafka.streams.v2.processor.state.GaugeStoreEntriesStateScheduleProcessor
import no.nav.aap.kafka.streams.v2.processor.state.MigrateStateInitProcessor
import no.nav.aap.kafka.streams.v2.topology
import no.nav.aap.ktor.config.loadConfig
import org.apache.kafka.clients.producer.Producer
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::server).start(wait = true)
}

internal fun Application.server(kafka: Streams = KafkaStreams()) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val config = loadConfig<Config>()

    install(MicrometerMetrics) { registry = prometheus }
    install(ContentNegotiation) { jackson { registerModule(JavaTimeModule()) } }

    val sykepengedagerProducer = kafka.createProducer(config.kafka, Topics.sykepengedager)

    Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }
    environment.monitor.subscribe(ApplicationStopping) {
        kafka.close()
        sykepengedagerProducer.close()
    }

    kafka.connect(
        config = config.kafka,
        registry = prometheus,
        topology = topology(prometheus, sykepengedagerProducer, config.toggle.settOppProdStream),
    )

    routing {
        actuator(prometheus, kafka)
    }
}

internal fun topology(
    prometheus: MeterRegistry,
    sykepengedagerProducer: Producer<String, SykepengedagerKafkaDto>,
    settOppProdStream: Boolean
): Topology = topology {
    val sykepengedagerKTable = consume(Tables.sykepengedager)

    sykepengedagerKTable.schedule(
        GaugeStoreEntriesStateScheduleProcessor(
            ktable = sykepengedagerKTable,
            interval = 2.toDuration(DurationUnit.MINUTES),
            registry = prometheus,
        )
    )

    sykepengedagerKTable.init(
        MigrateStateInitProcessor(
            ktable = sykepengedagerKTable,
            producer = sykepengedagerProducer,
        )
    )

    if (settOppProdStream) {
        spleisStream(sykepengedagerKTable)
        infotrygdStream(sykepengedagerKTable)
    }

    consume(Topics.subscribe)
        .leftJoinWith(sykepengedagerKTable)
        .map { _, existing -> existing ?: SykepengedagerKafkaDto(SykepengedagerKafkaDto.Response(null)) }
        .produce(Topics.sykepengedager)
}
