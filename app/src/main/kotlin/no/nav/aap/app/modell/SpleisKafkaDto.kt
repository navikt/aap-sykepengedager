package no.nav.aap.app.modell

import java.time.LocalDate

data class SpleisKafkaDto(
    val gjenståendeSykedager: Int?,
    val foreløpigBeregnetSluttPåSykepenger: LocalDate?,
)
