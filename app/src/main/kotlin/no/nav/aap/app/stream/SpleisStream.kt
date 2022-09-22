package no.nav.aap.app.stream

import no.nav.aap.app.kafka.Topics
import no.nav.aap.app.modell.SpleisKafkaDto
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.extension.*
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KTable
import org.apache.kafka.streams.kstream.Repartitioned
import org.slf4j.LoggerFactory

private val secureLog = LoggerFactory.getLogger("secureLog")

internal fun StreamsBuilder.spleisStream(sykepengedager: KTable<String, SykepengedagerKafkaDto>) {
    consume(Topics.spleis)
        .filterNotNull("spleis-sykepengedager-filter-tombstone")
        .filterNotNullBy("spleis-sykepengedager-filter-not-null-genstaende-sykedager-pre-repart") { spleisKafkaDto -> spleisKafkaDto.gjenståendeSykedager }
        .filterNotNullBy("spleis-sykepengedager-filter-not-null-slutt-sykepenger-pre-repart") { spleisKafkaDto -> spleisKafkaDto.foreløpigBeregnetSluttPåSykepenger }
        .repartition(
            Repartitioned
                .`as`<String?, SpleisKafkaDto?>("spleis-sykepengedager-repartition")
                .withKeySerde(Topics.spleis.keySerde)
                .withValueSerde(Topics.spleis.valueSerde)
                .withNumberOfPartitions(12)
        )
        .filterNotNullBy("spleis-sykepengedager-filter-not-null-genstaende-sykedager-post-repart") { spleisKafkaDto -> spleisKafkaDto.gjenståendeSykedager }
        .filterNotNullBy("spleis-sykepengedager-filter-not-null-slutt-sykepenger-post-repart") { spleisKafkaDto -> spleisKafkaDto.foreløpigBeregnetSluttPåSykepenger }
        .leftJoin(Topics.spleis with Topics.sykepengedager, sykepengedager)
        .mapValues("spleis-sykepengedager-map") { (spleisKafkaDto, gammelSykepengedagerKafkaDto) ->
            val gjenståendeSykedager = requireNotNull(spleisKafkaDto.gjenståendeSykedager)
            val foreløpigBeregnetSluttPåSykepenger = requireNotNull(spleisKafkaDto.foreløpigBeregnetSluttPåSykepenger)
            SykepengedagerKafkaDto(
                response = SykepengedagerKafkaDto.Response(
                    sykepengedager = SykepengedagerKafkaDto.Response.Sykepengedager(
                        gjenståendeSykedager = gjenståendeSykedager,
                        foreløpigBeregnetSluttPåSykepenger = foreløpigBeregnetSluttPåSykepenger,
                        kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
                    )
                )
            ) to gammelSykepengedagerKafkaDto
        }
        .peek("spleis-sykepengedager-peek-ny-gammel") { (nySykepengedagerKafkaDto, gammelSykepengedagerKafkaDto) ->
            if (gammelSykepengedagerKafkaDto != null)
                secureLog.info("oppdatert gjenstående sykedager i Spleis fra $gammelSykepengedagerKafkaDto til $nySykepengedagerKafkaDto")
            else
                secureLog.info("ny gjenstående sykedager i Spleis $nySykepengedagerKafkaDto")
        }
        .firstPairValue("spleis-sykepengedager-hent-ut-ny-sykepengedager")
        .produce(Topics.sykepengedager, "spleis-sykepengedager-produced")
}
