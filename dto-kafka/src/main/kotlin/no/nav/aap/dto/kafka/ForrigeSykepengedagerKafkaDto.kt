package no.nav.aap.dto.kafka

import java.time.LocalDate

data class ForrigeSykepengedagerKafkaDto(
    val gjenståendeSykedager: Int,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate,
    val kilde: SykepengedagerKafkaDto.Kilde,
)
