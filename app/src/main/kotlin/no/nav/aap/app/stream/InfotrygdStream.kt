package no.nav.aap.app.stream

import no.nav.aap.app.kafka.Topics
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.v2.KTable
import no.nav.aap.kafka.streams.v2.Topology
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun Topology.infotrygdStream(sykepengedager: KTable<SykepengedagerKafkaDto>) {
    consume(Topics.infotrygd)
        .filter { infotrygdKafkaDto -> infotrygdKafkaDto.after.UTBET_TOM != null }
        .filter { infotrygdKafkaDto -> infotrygdKafkaDto.after.MAX_DATO != null }
        .leftJoinWith(sykepengedager)
        .mapNotNull { infotrygdKafkaDto, gammelSykepengedagerKafkaDto ->
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val utbetTom = requireNotNull(infotrygdKafkaDto.after.UTBET_TOM).let { LocalDate.parse(it, formatter) }
            val maksdato = requireNotNull(infotrygdKafkaDto.after.MAX_DATO).let { LocalDate.parse(it, formatter) }
            if (utbetTom > maksdato) return@mapNotNull null
            SykepengedagerKafkaDto(
                response = SykepengedagerKafkaDto.Response(
                    sykepengedager = SykepengedagerKafkaDto.Response.Sykepengedager(
                        gjenståendeSykedager = utbetTom.gjenståendeSykedager(maksdato),
                        foreløpigBeregnetSluttPåSykepenger = maksdato,
                        kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
                    )
                )
            ) to gammelSykepengedagerKafkaDto
        }
        .secureLog { (nySykepengedagerKafkaDto, gammelSykepengedagerKafkaDto) ->
            if (gammelSykepengedagerKafkaDto != null) info("oppdatert gjenstående sykedager i Infotrygd fra $gammelSykepengedagerKafkaDto til $nySykepengedagerKafkaDto")
            else info("ny gjenstående sykedager i Infotrygd $nySykepengedagerKafkaDto")
        }
        .map(Pair<SykepengedagerKafkaDto, SykepengedagerKafkaDto?>::first)
        .produce(Topics.sykepengedager)
}

private fun LocalDate.gjenståendeSykedager(other: LocalDate) = this
    .plusDays(1)
    .datesUntil(other.plusDays(1))
    .toList()
    .count(LocalDate::erIkkeHelg)
    .coerceAtLeast(0)

private fun LocalDate.erIkkeHelg() = dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
