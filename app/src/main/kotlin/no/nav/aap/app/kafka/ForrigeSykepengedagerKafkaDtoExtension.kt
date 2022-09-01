package no.nav.aap.app.kafka

import no.nav.aap.dto.kafka.ForrigeSykepengedagerKafkaDto
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto

internal fun ForrigeSykepengedagerKafkaDto.toDto(): SykepengedagerKafkaDto = SykepengedagerKafkaDto(
    response = SykepengedagerKafkaDto.Response(
        gjenståendeSykedager = gjenståendeSykedager,
        foreløpigBeregnetSluttPåSykepenger = foreløpigBeregnetSluttPåSykepenger,
        kilde = kilde,
    ),
    version = SykepengedagerKafkaDto.VERSION,
)
