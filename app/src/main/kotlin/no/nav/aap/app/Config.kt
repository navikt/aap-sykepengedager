package no.nav.aap.app

import no.nav.aap.kafka.streams.v2.config.StreamsConfig

internal data class Config(
    val toggle: Toggle,
    val kafka: StreamsConfig,
)

internal data class Toggle(
    val settOppProdStream: Boolean,
)
