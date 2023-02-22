package no.nav.aap.app.stream

import no.nav.aap.app.kafka.Topics
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.v2.KTable
import no.nav.aap.kafka.streams.v2.Topology

internal fun Topology.spleisStream(sykepengedager: KTable<SykepengedagerKafkaDto>) {
    consume(Topics.spleis)
        .filter { spleisKafkaDto -> spleisKafkaDto.gjenståendeSykedager != null }
        .filter { spleisKafkaDto -> spleisKafkaDto.foreløpigBeregnetSluttPåSykepenger != null }
        .repartition()
        .filter { spleisKafkaDto -> spleisKafkaDto.gjenståendeSykedager != null }
        .filter { spleisKafkaDto -> spleisKafkaDto.foreløpigBeregnetSluttPåSykepenger != null }
        .leftJoinWith(sykepengedager)
        .map { spleisKafkaDto, gammelSykepengedagerKafkaDto ->
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
        .secureLog { (nySykepengedagerKafkaDto, gammelSykepengedagerKafkaDto) ->
            if (gammelSykepengedagerKafkaDto != null) info("oppdatert gjenstående sykedager i Spleis fra $gammelSykepengedagerKafkaDto til $nySykepengedagerKafkaDto")
            else info("ny gjenstående sykedager i Spleis $nySykepengedagerKafkaDto")
        }
        .map(Pair<SykepengedagerKafkaDto, SykepengedagerKafkaDto?>::first)
        .produce(Topics.sykepengedager)
}
