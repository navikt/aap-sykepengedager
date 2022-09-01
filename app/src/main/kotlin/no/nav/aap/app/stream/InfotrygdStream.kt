package no.nav.aap.app.stream

import no.nav.aap.app.kafka.Topics
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.extension.*
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KTable
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val secureLog = LoggerFactory.getLogger("secureLog")

internal fun StreamsBuilder.infotrygdStream(sykepengedager: KTable<String, SykepengedagerKafkaDto>) {
    consume(Topics.infotrygd)
        .filterNotNull("infotrygd-sykepengedager-filter-tombstone")
        .selectKey("infotrygd-sykepengedager-rekey") { key, _ ->
            "${key.substring(4, 6)}${key.substring(2, 4)}${key.substring(0, 2)}${key.substring(6)}"
        }
        .leftJoin(Topics.infotrygd with Topics.sykepengedager, sykepengedager)
        .mapValues("infotrygd-sykepengedager-map") { (infotrygdKafkaDto, gammelSykepengedagerKafkaDto) ->
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val utbetTom = LocalDate.parse(infotrygdKafkaDto.after.IS10_UTBET_TOM, formatter)
            val maksdato = LocalDate.parse(infotrygdKafkaDto.after.IS10_MAX, formatter)
            SykepengedagerKafkaDto(
                response = SykepengedagerKafkaDto.Response(
                    gjenståendeSykedager = utbetTom.gjenståendeSykedager(maksdato),
                    foreløpigBeregnetSluttPåSykepenger = maksdato,
                    kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
                )
            ) to gammelSykepengedagerKafkaDto
        }
        .peek("infotrygd-sykepengedager-peek-ny-gammel") { (nySykepengedagerKafkaDto, gammelSykepengedagerKafkaDto) ->
            if (gammelSykepengedagerKafkaDto != null)
                secureLog.info("oppdatert gjenstående sykedager i Infotrygd fra $gammelSykepengedagerKafkaDto til $nySykepengedagerKafkaDto")
            else
                secureLog.info("ny gjenstående sykedager i Infotrygd $nySykepengedagerKafkaDto")
        }
        .firstPairValue("infotrygd-sykepengedager-hent-ut-ny-sykepengedager")
        .produce(Topics.sykepengedager, "infotrygd-sykepengedager-produced")
}

private fun LocalDate.gjenståendeSykedager(other: LocalDate) = this
    .plusDays(1)
    .datesUntil(other.plusDays(1))
    .toList()
    .count(LocalDate::erIkkeHelg)
    .coerceAtLeast(0)

private fun LocalDate.erIkkeHelg() = dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
