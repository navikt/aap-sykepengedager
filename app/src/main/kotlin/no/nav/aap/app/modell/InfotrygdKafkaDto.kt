package no.nav.aap.app.modell

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

data class InfotrygdKafkaDto(
    val after: After,
) {
    data class After(
        @JsonProperty("UTBET_TOM")
        @JsonAlias("utbet_TOM")
        val UTBET_TOM: String?,
        @JsonProperty("MAX_DATO")
        @JsonAlias("max_DATO")
        val MAX_DATO: String?,
        @JsonProperty("F_NR")
        @JsonAlias("f_NR")
        val F_NR: String,
    )
}
