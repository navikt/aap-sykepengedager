package no.nav.aap.app

import io.ktor.server.testing.*
import no.nav.aap.app.kafka.SYKEPENGEDAGER_STORE_NAME
import no.nav.aap.app.kafka.Topics
import no.nav.aap.app.modell.InfotrygdKafkaDto
import no.nav.aap.app.modell.SpleisKafkaDto
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

            val fnrNormal = "29468230052"
            spleisTopic.produce(fnrNormal) {
                SpleisKafkaDto(
                    gjenståendeSykedager = 0,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnrNormal]
            assertNotNull(sykepengedagerKafkaDto)

            val expected = SykepengedagerKafkaDto(
                personident = fnrNormal,
                gjenståendeSykedager = 0,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
            )

            assertEquals(expected, sykepengedagerKafkaDto)
        }
    }

    @Test
    fun `Publiserer sykepengedager fra Infotrygd`() {
        withTestApp { mocks ->
            val infotrygdTopic = mocks.kafka.inputTopic(Topics.infotrygd)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            //Infotrygdformat
            val fnrInfotrygd = "82462930052"
            val fnrNormal = "29468230052"
            infotrygdTopic.produce(fnrInfotrygd) {
                InfotrygdKafkaDto(
                    after = InfotrygdKafkaDto.After(
                        IS10_UTBET_TOM = "20220506",
                        IS10_MAX = "20220907",
                        F_NR = fnrInfotrygd,
                    )
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnrNormal]
            assertNotNull(sykepengedagerKafkaDto)

            val expected = SykepengedagerKafkaDto(
                personident = fnrNormal,
                gjenståendeSykedager = 88,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 9, 7),
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            )

            assertEquals(expected, sykepengedagerKafkaDto)
        }
    }

    @Test
    fun `Ved beregning av gjenstående sykedager skal siste utbetalte dag ikke telles med og siste sykepengedag skal telles med`() {
        withTestApp { mocks ->
            val infotrygdTopic = mocks.kafka.inputTopic(Topics.infotrygd)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            //Infotrygdformat
            val fnrInfotrygd = "82462930052"
            val fnrNormal = "29468230052"
            infotrygdTopic.produce(fnrInfotrygd) {
                InfotrygdKafkaDto(
                    after = InfotrygdKafkaDto.After(
                        // Ikke en funksjonell gyldig utbetalingsdato
                        IS10_UTBET_TOM = "20220827",
                        IS10_MAX = "20220829",
                        F_NR = fnrInfotrygd,
                    )
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnrNormal]
            assertNotNull(sykepengedagerKafkaDto)

            val expected = SykepengedagerKafkaDto(
                personident = fnrNormal,
                gjenståendeSykedager = 1,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 8, 29),
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            )

            assertEquals(expected, sykepengedagerKafkaDto)
        }
    }

    @Test
    fun `Ny sykepengedager fra Spleis overskriver tidligere fra Spleis`() {
        withTestApp { mocks ->
            val spleisTopic = mocks.kafka.inputTopic(Topics.spleis)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            val fnr = "123"
            spleisTopic.produce(fnr) {
                SpleisKafkaDto(
                    gjenståendeSykedager = 5,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 24),
                )
            }
            spleisTopic.produce(fnr) {
                SpleisKafkaDto(
                    gjenståendeSykedager = 0,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnr]
            assertNotNull(sykepengedagerKafkaDto)

            val expected = SykepengedagerKafkaDto(
                personident = fnr,
                gjenståendeSykedager = 0,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
            )

            assertEquals(expected, sykepengedagerKafkaDto)
        }
    }

    @Test
    fun `Ny sykepengedager fra Spleis overskriver tidligere fra Infotrygd`() {
        withTestApp { mocks ->
            val spleisTopic = mocks.kafka.inputTopic(Topics.spleis)
            val infotrygdTopic = mocks.kafka.inputTopic(Topics.infotrygd)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            //Infotrygdformat
            val fnrInfotrygd = "82462930052"
            val fnrNormal = "29468230052"
            infotrygdTopic.produce(fnrInfotrygd) {
                InfotrygdKafkaDto(
                    after = InfotrygdKafkaDto.After(
                        IS10_UTBET_TOM = "20220617",
                        IS10_MAX = "20220624",
                        F_NR = fnrInfotrygd,
                    )
                )
            }
            spleisTopic.produce(fnrNormal) {
                SpleisKafkaDto(
                    gjenståendeSykedager = 0,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnrNormal]
            assertNotNull(sykepengedagerKafkaDto)

            val expected = SykepengedagerKafkaDto(
                personident = fnrNormal,
                gjenståendeSykedager = 0,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
            )

            assertEquals(expected, sykepengedagerKafkaDto)
        }
    }

    @Test
    fun `Ny sykepengedager fra Infotrygd overskriver tidligere fra Infotrygd`() {
        withTestApp { mocks ->
            val infotrygdTopic = mocks.kafka.inputTopic(Topics.infotrygd)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            //Infotrygdformat
            val fnrInfotrygd = "82462930052"
            val fnrNormal = "29468230052"
            infotrygdTopic.produce(fnrInfotrygd) {
                InfotrygdKafkaDto(
                    after = InfotrygdKafkaDto.After(
                        IS10_UTBET_TOM = "20220617",
                        IS10_MAX = "20220624",
                        F_NR = fnrInfotrygd,
                    )
                )
            }
            infotrygdTopic.produce(fnrInfotrygd) {
                InfotrygdKafkaDto(
                    after = InfotrygdKafkaDto.After(
                        IS10_UTBET_TOM = "20220626",
                        IS10_MAX = "20220626",
                        F_NR = fnrInfotrygd,
                    )
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnrNormal]
            assertNotNull(sykepengedagerKafkaDto)

            val expected = SykepengedagerKafkaDto(
                personident = fnrNormal,
                gjenståendeSykedager = 0,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            )

            assertEquals(expected, sykepengedagerKafkaDto)
        }
    }

    @Test
    fun `Ny sykepengedager fra Infotrygd overskriver tidligere fra Spleis`() {
        withTestApp { mocks ->
            val spleisTopic = mocks.kafka.inputTopic(Topics.spleis)
            val infotrygdTopic = mocks.kafka.inputTopic(Topics.infotrygd)
            val stateStore = mocks.kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

            //Infotrygdformat
            val fnrInfotrygd = "82462930052"
            val fnrNormal = "29468230052"
            spleisTopic.produce(fnrNormal) {
                SpleisKafkaDto(
                    gjenståendeSykedager = 5,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 24),
                )
            }
            infotrygdTopic.produce(fnrInfotrygd) {
                InfotrygdKafkaDto(
                    after = InfotrygdKafkaDto.After(
                        IS10_UTBET_TOM = "20220626",
                        IS10_MAX = "20220626",
                        F_NR = fnrInfotrygd,
                    )
                )
            }

            val sykepengedagerKafkaDto = stateStore[fnrNormal]
            assertNotNull(sykepengedagerKafkaDto)

            val expected = SykepengedagerKafkaDto(
                personident = fnrNormal,
                gjenståendeSykedager = 0,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            )

            assertEquals(expected, sykepengedagerKafkaDto)
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
