package no.nav.aap.app.stream

import no.nav.aap.app.kafka.Topics
import no.nav.aap.app.modell.SykepengedagerKafkaDto
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
        .mapValues("infotrygd-sykepengedager-map") { key, (infotrygd, gammelSøkereKafkaDto) ->
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val utbetTom = LocalDate.parse(infotrygd.after.IS10_UTBET_TOM, formatter)
            val maksdato = LocalDate.parse(infotrygd.after.IS10_MAX, formatter)
            SykepengedagerKafkaDto(
                personident = key,
                gjenståendeSykedager = utbetTom.gjenståendeSykedager(maksdato),
                foreløpigBeregnetSluttPåSykepenger = maksdato,
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            ) to gammelSøkereKafkaDto
        }
        .peek("infotrygd-sykepengedager-peek-ny-gammel") { (søkereKafkaDto, gammelSøkereKafkaDto) ->
            if (gammelSøkereKafkaDto != null)
                secureLog.info("oppdatert gjenstående sykedager i Infotrygd fra $gammelSøkereKafkaDto til $søkereKafkaDto")
            else
                secureLog.info("ny gjenstående sykedager i Infotrygd $søkereKafkaDto")
        }
        .first()
        .produce(Topics.sykepengedager, "infotrygd-sykepengedager-produced")
}

private fun LocalDate.gjenståendeSykedager(other: LocalDate) = this
    .datesUntil(other)
    .toList()
    .count(LocalDate::erIkkeHelg)
    .coerceAtLeast(0)

private fun LocalDate.erIkkeHelg() = dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
