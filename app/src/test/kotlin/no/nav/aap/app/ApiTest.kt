package no.nav.aap.app

import io.ktor.server.testing.*
import no.nav.aap.app.kafka.SYKEPENGEDAGER_STORE_NAME
import no.nav.aap.app.kafka.Topics
import no.nav.aap.app.modell.SykepengedagerKafkaDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import java.time.LocalDate

internal class ApiTest {

    @Test
    fun `Publiserer sykepengedager fra Spleis`() {
        withTestApp { mocks ->
            val spleisTopic = mocks.kafka.inputTopic(Topics.spleis)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            val fnr = "123"
            spleisTopic.produce(fnr) {
                SykepengedagerKafkaDto(
                    personident = fnr,
                    gjenståendeSykedager = 0,
                    maksdato = LocalDate.of(2022, 6, 26),
                    kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnr]
            assertNotNull(sykepengedagerKafkaDto)

            val actual = SykepengedagerKafkaDto(
                personident = fnr,
                gjenståendeSykedager = 0,
                maksdato = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
            )

            assertEquals(sykepengedagerKafkaDto, actual)
        }
    }

    @Test
    fun `Publiserer sykepengedager fra Infotrygd`() {
        withTestApp { mocks ->
            val infotrygdTopic = mocks.kafka.inputTopic(Topics.infotrygd)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            val fnr = "123"
            infotrygdTopic.produce(fnr) {
                SykepengedagerKafkaDto(
                    personident = fnr,
                    gjenståendeSykedager = 0,
                    maksdato = LocalDate.of(2022, 6, 26),
                    kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnr]
            assertNotNull(sykepengedagerKafkaDto)

            val actual = SykepengedagerKafkaDto(
                personident = fnr,
                gjenståendeSykedager = 0,
                maksdato = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            )

            assertEquals(sykepengedagerKafkaDto, actual)
        }
    }

    @Test
    fun `Ny sykepengedager fra Spleis overskriver tidligere fra Spleis`() {
        withTestApp { mocks ->
            val spleisTopic = mocks.kafka.inputTopic(Topics.spleis)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            val fnr = "123"
            spleisTopic.produce(fnr) {
                SykepengedagerKafkaDto(
                    personident = fnr,
                    gjenståendeSykedager = 5,
                    maksdato = LocalDate.of(2022, 6, 24),
                    kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
                )
            }
            spleisTopic.produce(fnr) {
                SykepengedagerKafkaDto(
                    personident = fnr,
                    gjenståendeSykedager = 0,
                    maksdato = LocalDate.of(2022, 6, 26),
                    kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnr]
            assertNotNull(sykepengedagerKafkaDto)

            val actual = SykepengedagerKafkaDto(
                personident = fnr,
                gjenståendeSykedager = 0,
                maksdato = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
            )

            assertEquals(sykepengedagerKafkaDto, actual)
        }
    }

    @Test
    fun `Ny sykepengedager fra Spleis overskriver tidligere fra Infotrygd`() {
        withTestApp { mocks ->
            val spleisTopic = mocks.kafka.inputTopic(Topics.spleis)
            val infotrygdTopic = mocks.kafka.inputTopic(Topics.infotrygd)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            val fnr = "123"
            infotrygdTopic.produce(fnr) {
                SykepengedagerKafkaDto(
                    personident = fnr,
                    gjenståendeSykedager = 5,
                    maksdato = LocalDate.of(2022, 6, 24),
                    kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
                )
            }
            spleisTopic.produce(fnr) {
                SykepengedagerKafkaDto(
                    personident = fnr,
                    gjenståendeSykedager = 0,
                    maksdato = LocalDate.of(2022, 6, 26),
                    kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnr]
            assertNotNull(sykepengedagerKafkaDto)

            val actual = SykepengedagerKafkaDto(
                personident = fnr,
                gjenståendeSykedager = 0,
                maksdato = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
            )

            assertEquals(sykepengedagerKafkaDto, actual)
        }
    }

    @Test
    fun `Ny sykepengedager fra Infotrygd overskriver tidligere fra Infotrygd`() {
        withTestApp { mocks ->
            val infotrygdTopic = mocks.kafka.inputTopic(Topics.infotrygd)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            val fnr = "123"
            infotrygdTopic.produce(fnr) {
                SykepengedagerKafkaDto(
                    personident = fnr,
                    gjenståendeSykedager = 5,
                    maksdato = LocalDate.of(2022, 6, 24),
                    kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
                )
            }
            infotrygdTopic.produce(fnr) {
                SykepengedagerKafkaDto(
                    personident = fnr,
                    gjenståendeSykedager = 0,
                    maksdato = LocalDate.of(2022, 6, 26),
                    kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnr]
            assertNotNull(sykepengedagerKafkaDto)

            val actual = SykepengedagerKafkaDto(
                personident = fnr,
                gjenståendeSykedager = 0,
                maksdato = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            )

            assertEquals(sykepengedagerKafkaDto, actual)
        }
    }

    @Test
    fun `Ny sykepengedager fra Infotrygd overskriver tidligere fra Spleis`() {
        withTestApp { mocks ->
            val spleisTopic = mocks.kafka.inputTopic(Topics.spleis)
            val infotrygdTopic = mocks.kafka.inputTopic(Topics.infotrygd)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            val fnr = "123"
            spleisTopic.produce(fnr) {
                SykepengedagerKafkaDto(
                    personident = fnr,
                    gjenståendeSykedager = 5,
                    maksdato = LocalDate.of(2022, 6, 24),
                    kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
                )
            }
            infotrygdTopic.produce(fnr) {
                SykepengedagerKafkaDto(
                    personident = fnr,
                    gjenståendeSykedager = 0,
                    maksdato = LocalDate.of(2022, 6, 26),
                    kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnr]
            assertNotNull(sykepengedagerKafkaDto)

            val actual = SykepengedagerKafkaDto(
                personident = fnr,
                gjenståendeSykedager = 0,
                maksdato = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            )

            assertEquals(sykepengedagerKafkaDto, actual)
        }
    }
}

private fun withTestApp(test: ApplicationTestBuilder.(mocks: Mocks) -> Unit) = Mocks().use { mocks ->
    EnvironmentVariables(containerProperties()).execute {
        testApplication {
            application {
                server(mocks.kafka)
                this@testApplication.test(mocks)
            }
        }
    }
}

private fun containerProperties(): Map<String, String> = mapOf(
    "KAFKA_STREAMS_APPLICATION_ID" to "sykepengedager",
    "KAFKA_BROKERS" to "mock://kafka",
    "KAFKA_TRUSTSTORE_PATH" to "",
    "KAFKA_KEYSTORE_PATH" to "",
    "KAFKA_CREDSTORE_PASSWORD" to "",
    "KAFKA_CLIENT_ID" to "sykepengedager",
    "KAFKA_GROUP_ID" to "sykepengedager-1",
    "KAFKA_SCHEMA_REGISTRY" to "mock://schema-registry",
    "KAFKA_SCHEMA_REGISTRY_USER" to "",
    "KAFKA_SCHEMA_REGISTRY_PASSWORD" to "",
)
