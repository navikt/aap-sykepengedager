package no.nav.aap.dto.kafka

import no.nav.aap.kafka.serde.json.Migratable
import java.time.LocalDate

data class SykepengedagerKafkaDto(
    val response: Response?,
    val version: Int = VERSION,
) : Migratable {
    data class Response(
        val gjenståendeSykedager: Int,
        val foreløpigBeregnetSluttPåSykepenger: LocalDate,
        val kilde: Kilde,
    )

    private var erMigrertAkkuratNå: Boolean = false

    companion object {
        const val VERSION = 1
    }

    enum class Kilde {
        SPLEIS, INFOTRYGD,
    }

    override fun erMigrertAkkuratNå(): Boolean = erMigrertAkkuratNå

    override fun markerSomMigrertAkkuratNå() {
        erMigrertAkkuratNå = true
    }
}
