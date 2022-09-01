package no.nav.aap.app.stream

import no.nav.aap.app.kafka.Topics
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.extension.filterNotNull
import no.nav.aap.kafka.streams.extension.join
import no.nav.aap.kafka.streams.extension.produce
import no.nav.aap.kafka.streams.extension.secondPairValue
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KTable

internal fun KStream<String, SykepengedagerKafkaDto?>.reproduce(sykepengedagerTable: KTable<String, SykepengedagerKafkaDto>) {
    filterNotNull("filter-sykepengedager-tombstone")
        .filter { _, value -> value.response == null }
        .join(Topics.sykepengedager with Topics.sykepengedager, sykepengedagerTable)
        .secondPairValue("select-state-stored-sykepengedager")
        .produce(Topics.sykepengedager, "sykependedager-reproduced")
}
