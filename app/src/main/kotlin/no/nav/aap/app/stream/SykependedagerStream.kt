package no.nav.aap.app.stream

import no.nav.aap.app.kafka.Topics
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.v2.KTable
import no.nav.aap.kafka.streams.v2.stream.ConsumedKStream

internal fun ConsumedKStream<SykepengedagerKafkaDto>.reproduce(sykepengedagerTable: KTable<SykepengedagerKafkaDto>) {
    filter { value -> value.response == null }
        .leftJoinWith(sykepengedagerTable)
        .map { _, rightSide -> rightSide ?: SykepengedagerKafkaDto(SykepengedagerKafkaDto.Response(null)) }
        .produce(Topics.sykepengedager)
}
