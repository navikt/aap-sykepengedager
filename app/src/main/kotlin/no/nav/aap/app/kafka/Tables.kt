package no.nav.aap.app.kafka

import no.nav.aap.kafka.streams.v2.Table

object Tables {
    val sykepengedager = Table(Topics.sykepengedager, stateStoreName = "sykepengedager-state-store-v1")
}
