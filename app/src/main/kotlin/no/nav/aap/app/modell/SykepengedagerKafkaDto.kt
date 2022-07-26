package no.nav.aap.app.modell

import java.time.LocalDate

data class SykepengedagerKafkaDto(
    val personident: String,
    val gjenståendeSykedager: Int,
    val maksdato: LocalDate,
    val kilde: Kilde,
) {
    enum class Kilde {
        SPLEIS, INFOTRYGD,
    }
}
