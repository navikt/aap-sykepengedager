package no.nav.aap.app.kafka

import no.nav.aap.kafka.streams.Table

internal const val SYKEPENGEDAGER_STORE_NAME = "sykepengedager-state-store-v1"

object Tables {
    val sykepengedager = Table("sokere", Topics.sykepengedager, false, SYKEPENGEDAGER_STORE_NAME)
}
