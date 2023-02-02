package no.nav.aap.app

import no.nav.aap.kafka.streams.KStreamsConfig

internal data class Config(
    val toggle: Toggle,
    val kafka: KStreamsConfig,
)

internal data class Toggle(
    val settOppProdStream: Boolean,
)
