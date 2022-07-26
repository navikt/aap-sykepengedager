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

internal fun StreamsBuilder.spleisStream(sykepengedager: KTable<String, SykepengedagerKafkaDto>) {
    consume(Topics.spleis)
        .filterNotNull("spleis-sykepengedager-filter-tombstone")
        .leftJoin(Topics.spleis with Topics.sykepengedager, sykepengedager)
        .peek("spleis-sykepengedager-peek-ny-gammel") { (søkereKafkaDto, gammelSøkereKafkaDto) ->
            if (gammelSøkereKafkaDto != null)
                secureLog.info("oppdatert gjenstående sykedager i Spleis fra $gammelSøkereKafkaDto til $søkereKafkaDto")
            else
                secureLog.info("ny gjenstående sykedager i Spleis $søkereKafkaDto")
        }
        .first()
        .mapValues("spleis-sykepengedager-map-dto") { sykepengedagerKafkaDto ->
            sykepengedagerKafkaDto.copy(kilde = SykepengedagerKafkaDto.Kilde.SPLEIS)
        }
        .produce(Topics.sykepengedager, "spleis-sykepengedager-produced")
}
