package no.nav.aap.app.stream

import no.nav.aap.app.kafka.Topics
import no.nav.aap.app.modell.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.extension.*
import no.nav.aap.kafka.streams.named
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KTable
import org.slf4j.LoggerFactory

private val secureLog = LoggerFactory.getLogger("secureLog")

internal fun StreamsBuilder.infotrygdStream(sykepengedager: KTable<String, SykepengedagerKafkaDto>) {
    consume(Topics.infotrygd)
        .filterNotNull("infotrygd-sykepengedager-filter-tombstone")
        .leftJoin(Topics.infotrygd with Topics.sykepengedager, sykepengedager)
        .peek("infotrygd-sykepengedager-peek-ny-gammel") { (søkereKafkaDto, gammelSøkereKafkaDto) ->
            if (gammelSøkereKafkaDto != null)
                secureLog.info("oppdatert gjenstående sykedager i Infotrygd fra $gammelSøkereKafkaDto til $søkereKafkaDto")
            else
                secureLog.info("ny gjenstående sykedager i Infotrygd $søkereKafkaDto")
        }
        .first()
        .mapValues("infotrygd-sykepengedager-map-dto") { sykepengedagerKafkaDto ->
            sykepengedagerKafkaDto.copy(kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD)
        }
        .produce(Topics.sykepengedager, "infotrygd-sykepengedager-produced")
}
