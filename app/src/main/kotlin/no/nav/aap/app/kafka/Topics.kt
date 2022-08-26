package no.nav.aap.app.kafka

import no.nav.aap.app.modell.*
import no.nav.aap.kafka.serde.json.JsonSerde
import no.nav.aap.kafka.streams.Topic

object Topics {
    //TODO: Finn riktig navn på topic
    val spleis = Topic("aap.sykepengedager.spleis.v1", JsonSerde.jackson<SykepengedagerKafkaDto>())
    val infotrygd = Topic("aap.sykepengedager.infotrygd.v1", JsonSerde.jackson<InfotrygdKafkaDto>())
    val sykepengedager = Topic("aap.sykepengedager.v1", JsonSerde.jackson<SykepengedagerKafkaDto>())
}
