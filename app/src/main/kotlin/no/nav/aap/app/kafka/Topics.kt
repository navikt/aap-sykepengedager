package no.nav.aap.app.kafka

import no.nav.aap.app.modell.InfotrygdKafkaDto
import no.nav.aap.app.modell.SpleisKafkaDto
import no.nav.aap.dto.kafka.ForrigeSykepengedagerKafkaDto
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto.Companion.VERSION
import no.nav.aap.kafka.streams.v2.Topic
import no.nav.aap.kafka.streams.v2.serde.JsonSerde

object Topics {
    val spleis = Topic("tbd.utbetaling", JsonSerde.jackson<SpleisKafkaDto>())
    val infotrygd = Topic("aap.sykepengedager.infotrygd.v1", JsonSerde.jackson<InfotrygdKafkaDto>())
    val sykepengedager = Topic("aap.sykepengedager.v1", JsonSerde.jackson(VERSION, ForrigeSykepengedagerKafkaDto::toDto))
}
