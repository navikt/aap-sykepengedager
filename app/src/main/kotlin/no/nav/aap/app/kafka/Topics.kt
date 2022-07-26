package no.nav.aap.app.kafka

import no.nav.aap.app.modell.*
import no.nav.aap.kafka.serde.json.JsonSerde
import no.nav.aap.kafka.streams.Topic

object Topics {
    //TODO: Finn riktig navn på topic
    val spleis = Topic("aap.spleis.v1", JsonSerde.jackson<SykepengedagerKafkaDto>())
    //TODO: Finn riktig navn på topic
    val infotrygd = Topic("aap.infotrygd.v1", JsonSerde.jackson<SykepengedagerKafkaDto>())
    val sykepengedager = Topic("aap.sykepengedager.v1", JsonSerde.jackson<SykepengedagerKafkaDto>())
}
