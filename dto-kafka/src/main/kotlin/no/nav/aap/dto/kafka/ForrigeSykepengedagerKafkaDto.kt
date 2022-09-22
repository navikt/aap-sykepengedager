package no.nav.aap.dto.kafka

import java.time.LocalDate

data class ForrigeSykepengedagerKafkaDto(
    val response: Response?,
    val version: Int = SykepengedagerKafkaDto.VERSION - 1,
){
    data class Response(
        val gjenståendeSykedager: Int,
        val foreløpigBeregnetSluttPåSykepenger: LocalDate,
        val kilde: Kilde,
    )

    enum class Kilde {
        SPLEIS, INFOTRYGD,
    }
}
