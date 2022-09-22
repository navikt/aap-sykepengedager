package no.nav.aap.app.kafka

import no.nav.aap.dto.kafka.ForrigeSykepengedagerKafkaDto
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto

internal fun ForrigeSykepengedagerKafkaDto.toDto(): SykepengedagerKafkaDto {
    val responseNotNull = requireNotNull(response)
    return SykepengedagerKafkaDto(
        response = SykepengedagerKafkaDto.Response(
            sykepengedager = SykepengedagerKafkaDto.Response.Sykepengedager(
                gjenståendeSykedager = responseNotNull.gjenståendeSykedager,
                foreløpigBeregnetSluttPåSykepenger = responseNotNull.foreløpigBeregnetSluttPåSykepenger,
                kilde = enumValueOf(responseNotNull.kilde.name),
            ),
        )
    )
}
