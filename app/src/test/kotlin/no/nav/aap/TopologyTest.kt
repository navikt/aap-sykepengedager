package no.nav.aap

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.app.kafka.SYKEPENGEDAGER_STORE_NAME
import no.nav.aap.app.kafka.Topics
import no.nav.aap.app.modell.InfotrygdKafkaDto
import no.nav.aap.app.modell.SpleisKafkaDto
import no.nav.aap.app.topology
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.KStreamsConfig
import no.nav.aap.kafka.streams.test.KafkaStreamsMock
import no.nav.aap.kafka.streams.test.readAndAssert
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.streams.TestInputTopic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TopologyTest {
    private val kafka: KafkaStreamsMock = KafkaStreamsMock()

    @BeforeAll
    fun initiate() = kafka.connect(
        config = KStreamsConfig("test", "mock://aiven"),
        registry = SimpleMeterRegistry(),
        topology = topology(SimpleMeterRegistry(), MockProducer())
    )

    @AfterAll
    fun teardown() = kafka.close()

    @Test
    fun `Publiserer sykepengedager fra Spleis`() {
        val spleisTopic = kafka.inputTopic(Topics.spleis)
        val stateStore = kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

        val fnr = "29468230052"
        spleisTopic.produce(fnr) {
            SpleisKafkaDto(
                gjenståendeSykedager = 0,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
            )
        }

        val sykepengedagerKafkaDto = stateStore[fnr]
        assertNotNull(sykepengedagerKafkaDto)

        val expected = SykepengedagerKafkaDto(
            response = SykepengedagerKafkaDto.Response(
                gjenståendeSykedager = 0,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
    }

    @Test
    fun `Publiserer sykepengedager fra Infotrygd`() {
        val infotrygdTopic = kafka.inputTopic(Topics.infotrygd)
        val stateStore = kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

        val fnr = "29468230052"
        infotrygdTopic.produce(fnr) {
            InfotrygdKafkaDto(
                after = InfotrygdKafkaDto.After(
                    UTBET_TOM = "20220506",
                    MAX_DATO = "20220907",
                    F_NR = fnr,
                )
            )
        }

        val sykepengedagerKafkaDto = stateStore[fnr]
        assertNotNull(sykepengedagerKafkaDto)

        val expected = SykepengedagerKafkaDto(
            response = SykepengedagerKafkaDto.Response(
                gjenståendeSykedager = 88,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 9, 7),
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
    }

    @Test
    fun `Ved beregning av gjenstående sykedager skal siste utbetalte dag ikke telles med og siste sykepengedag skal telles med`() {
        val infotrygdTopic = kafka.inputTopic(Topics.infotrygd)
        val stateStore = kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

        val fnr = "29468230052"
        infotrygdTopic.produce(fnr) {
            InfotrygdKafkaDto(
                after = InfotrygdKafkaDto.After(
                    // Ikke en funksjonell gyldig utbetalingsdato
                    UTBET_TOM = "20220827",
                    MAX_DATO = "20220829",
                    F_NR = fnr,
                )
            )
        }

        val sykepengedagerKafkaDto = stateStore[fnr]
        assertNotNull(sykepengedagerKafkaDto)

        val expected = SykepengedagerKafkaDto(
            response = SykepengedagerKafkaDto.Response(
                gjenståendeSykedager = 1,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 8, 29),
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
    }

    @Test
    fun `Ny sykepengedager fra Spleis overskriver tidligere fra Spleis`() {
        val spleisTopic = kafka.inputTopic(Topics.spleis)
        val stateStore = kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

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
            response = SykepengedagerKafkaDto.Response(
                gjenståendeSykedager = 0,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
    }

    @Test
    fun `Ny sykepengedager fra Spleis overskriver tidligere fra Infotrygd`() {
        val spleisTopic = kafka.inputTopic(Topics.spleis)
        val infotrygdTopic = kafka.inputTopic(Topics.infotrygd)
        val stateStore = kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

        val fnr = "29468230052"
        infotrygdTopic.produce(fnr) {
            InfotrygdKafkaDto(
                after = InfotrygdKafkaDto.After(
                    UTBET_TOM = "20220617",
                    MAX_DATO = "20220624",
                    F_NR = fnr,
                )
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
        assertNotNull(sykepengedagerKafkaDto.response)

        val expected = SykepengedagerKafkaDto(
            response = SykepengedagerKafkaDto.Response(
                gjenståendeSykedager = 0,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
    }

    @Test
    fun `Ny sykepengedager fra Infotrygd overskriver tidligere fra Infotrygd`() {
        val infotrygdTopic = kafka.inputTopic(Topics.infotrygd)
        val stateStore = kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

        val fnr = "29468230052"
        infotrygdTopic.produce(fnr) {
            InfotrygdKafkaDto(
                after = InfotrygdKafkaDto.After(
                    UTBET_TOM = "20220617",
                    MAX_DATO = "20220624",
                    F_NR = fnr,
                )
            )
        }
        infotrygdTopic.produce(fnr) {
            InfotrygdKafkaDto(
                after = InfotrygdKafkaDto.After(
                    UTBET_TOM = "20220626",
                    MAX_DATO = "20220626",
                    F_NR = fnr,
                )
            )
        }

        val sykepengedagerKafkaDto = stateStore[fnr]
        assertNotNull(sykepengedagerKafkaDto)

        val expected = SykepengedagerKafkaDto(
            response = SykepengedagerKafkaDto.Response(
                gjenståendeSykedager = 0,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
    }

    @Test
    fun `Ny sykepengedager fra Infotrygd overskriver tidligere fra Spleis`() {
        val spleisTopic = kafka.inputTopic(Topics.spleis)
        val infotrygdTopic = kafka.inputTopic(Topics.infotrygd)
        val stateStore = kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

        val fnr = "29468230052"
        spleisTopic.produce(fnr) {
            SpleisKafkaDto(
                gjenståendeSykedager = 5,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 24),
            )
        }
        infotrygdTopic.produce(fnr) {
            InfotrygdKafkaDto(
                after = InfotrygdKafkaDto.After(
                    UTBET_TOM = "20220626",
                    MAX_DATO = "20220626",
                    F_NR = fnr,
                )
            )
        }

        val sykepengedagerKafkaDto = stateStore[fnr]
        assertNotNull(sykepengedagerKafkaDto)

        val expected = SykepengedagerKafkaDto(
            response = SykepengedagerKafkaDto.Response(
                gjenståendeSykedager = 0,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
    }

    @Test
    fun `en tidligere sykepengedager blir reprodusert ved tom response`() {
        val spleisTopic = kafka.inputTopic(Topics.spleis)
        val sykepengedagerTopic = kafka.inputTopic(Topics.sykepengedager)
        val sykepengedagerOutputTopic = kafka.outputTopic(Topics.sykepengedager)
        val stateStore = kafka.getStore<SykepengedagerKafkaDto>(SYKEPENGEDAGER_STORE_NAME)

        spleisTopic.produce("123") {
            SpleisKafkaDto(
                gjenståendeSykedager = 5,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 24),
            )
        }

        val sykepengedagerFraSpleis = stateStore["123"]
        assertNotNull(sykepengedagerFraSpleis)
        assertNotNull(sykepengedagerFraSpleis.response)

        sykepengedagerTopic.produce("123") {
            SykepengedagerKafkaDto(response = null)
        }

        val sykepengedagerEtterRequest = stateStore["123"]
        assertEquals(sykepengedagerFraSpleis, sykepengedagerEtterRequest)

        sykepengedagerOutputTopic.readAndAssert()
            .hasNumberOfRecordsForKey("123", 2)
            .hasValuesForPredicate("123", 2) { it.response != null }
            .hasLastValueMatching { it === sykepengedagerFraSpleis }
    }
}

private inline fun <reified V : Any> TestInputTopic<String, V>.produce(key: String, value: () -> V) =
    pipeInput(key, value())
