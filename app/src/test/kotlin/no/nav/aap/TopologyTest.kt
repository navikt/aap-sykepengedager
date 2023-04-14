package no.nav.aap

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.app.kafka.Tables
import no.nav.aap.app.kafka.Topics
import no.nav.aap.app.modell.InfotrygdKafkaDto
import no.nav.aap.app.modell.SpleisKafkaDto
import no.nav.aap.app.topology
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.v2.config.StreamsConfig
import no.nav.aap.kafka.streams.v2.test.StreamsMock
import org.apache.kafka.clients.producer.MockProducer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TopologyTest {
    private val kafka: StreamsMock = StreamsMock()

    @BeforeEach
    fun initiate() = kafka.connect(
        config = StreamsConfig("test", "mock://aiven"),
        registry = SimpleMeterRegistry(),
        topology = topology(SimpleMeterRegistry(), MockProducer(), true)
    )

    @AfterEach
    fun teardown() = kafka.close()

    @Test
    fun `Publiserer sykepengedager fra Spleis`() {
        val spleisTopic = kafka.testTopic(Topics.spleis)
        val sykepengedagerTopic = kafka.testTopic(Topics.sykepengedager)
        val stateStore = kafka.getStore(Tables.sykepengedager)

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
                sykepengedager = SykepengedagerKafkaDto.Response.Sykepengedager(
                    gjenståendeSykedager = 0,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                    kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
                )
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
        sykepengedagerTopic.assertThat().hasNumberOfRecordsForKey(fnr, 1)
    }

    @Test
    fun `Publiserer sykepengedager fra Infotrygd`() {
        val infotrygdTopic = kafka.testTopic(Topics.infotrygd)
        val sykepengedagerTopic = kafka.testTopic(Topics.sykepengedager)
        val stateStore = kafka.getStore(Tables.sykepengedager)

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
                sykepengedager = SykepengedagerKafkaDto.Response.Sykepengedager(
                    gjenståendeSykedager = 88,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 9, 7),
                    kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
                )
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
        sykepengedagerTopic.assertThat().hasNumberOfRecordsForKey(fnr, 1)
    }

    @Test
    fun `Ved beregning av gjenstående sykedager skal siste utbetalte dag ikke telles med og siste sykepengedag skal telles med`() {
        val infotrygdTopic = kafka.testTopic(Topics.infotrygd)
        val sykepengedagerTopic = kafka.testTopic(Topics.sykepengedager)
        val stateStore = kafka.getStore(Tables.sykepengedager)

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
                sykepengedager = SykepengedagerKafkaDto.Response.Sykepengedager(
                    gjenståendeSykedager = 1,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 8, 29),
                    kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
                )
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
        sykepengedagerTopic.assertThat().hasNumberOfRecordsForKey(fnr, 1)
    }

    @Test
    fun `Ny sykepengedager fra Spleis overskriver tidligere fra Spleis`() {
        val spleisTopic = kafka.testTopic(Topics.spleis)
        val sykepengedagerTopic = kafka.testTopic(Topics.sykepengedager)
        val stateStore = kafka.getStore(Tables.sykepengedager)

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
                sykepengedager = SykepengedagerKafkaDto.Response.Sykepengedager(
                    gjenståendeSykedager = 0,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                    kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
                )
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
        sykepengedagerTopic.assertThat().hasNumberOfRecordsForKey(fnr, 2)
    }

    @Test
    fun `Ny sykepengedager fra Spleis overskriver tidligere fra Infotrygd`() {
        val spleisTopic = kafka.testTopic(Topics.spleis)
        val infotrygdTopic = kafka.testTopic(Topics.infotrygd)
        val sykepengedagerTopic = kafka.testTopic(Topics.sykepengedager)
        val stateStore = kafka.getStore(Tables.sykepengedager)

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
        assertNotNull(sykepengedagerKafkaDto?.response)

        val expected = SykepengedagerKafkaDto(
            response = SykepengedagerKafkaDto.Response(
                sykepengedager = SykepengedagerKafkaDto.Response.Sykepengedager(
                    gjenståendeSykedager = 0,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                    kilde = SykepengedagerKafkaDto.Kilde.SPLEIS,
                )
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
        sykepengedagerTopic.assertThat().hasNumberOfRecordsForKey(fnr, 2)
    }

    @Test
    fun `Ny sykepengedager fra Infotrygd overskriver tidligere fra Infotrygd`() {
        val infotrygdTopic = kafka.testTopic(Topics.infotrygd)
        val sykepengedagerTopic = kafka.testTopic(Topics.sykepengedager)
        val stateStore = kafka.getStore(Tables.sykepengedager)

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
                sykepengedager = SykepengedagerKafkaDto.Response.Sykepengedager(
                    gjenståendeSykedager = 0,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                    kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
                )
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
        sykepengedagerTopic.assertThat().hasNumberOfRecordsForKey(fnr, 2)
    }

    @Test
    fun `Ny sykepengedager fra Infotrygd overskriver tidligere fra Spleis`() {
        val spleisTopic = kafka.testTopic(Topics.spleis)
        val infotrygdTopic = kafka.testTopic(Topics.infotrygd)
        val sykepengedagerTopic = kafka.testTopic(Topics.sykepengedager)
        val stateStore = kafka.getStore(Tables.sykepengedager)

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
                sykepengedager = SykepengedagerKafkaDto.Response.Sykepengedager(
                    gjenståendeSykedager = 0,
                    foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 26),
                    kilde = SykepengedagerKafkaDto.Kilde.INFOTRYGD,
                )
            )
        )

        assertEquals(expected, sykepengedagerKafkaDto)
        sykepengedagerTopic.assertThat().hasNumberOfRecordsForKey(fnr, 2)
    }

    @Test
    fun `en tidligere sykepengedager blir reprodusert ved tom response`() {
        val spleisTopic = kafka.testTopic(Topics.spleis)
        val sykepengedagerTopic = kafka.testTopic(Topics.sykepengedager)
        val subscriberTopic = kafka.testTopic(Topics.subscribe)
        val stateStore = kafka.getStore(Tables.sykepengedager)

        spleisTopic.produce("123") {
            SpleisKafkaDto(
                gjenståendeSykedager = 5,
                foreløpigBeregnetSluttPåSykepenger = LocalDate.of(2022, 6, 24),
            )
        }

        val sykepengedagerFraSpleis = stateStore["123"]
        assertNotNull(sykepengedagerFraSpleis?.response)

        subscriberTopic.produce("123") {
            "".toByteArray()
        }

        val sykepengedagerEtterRequest = stateStore["123"]
        assertEquals(sykepengedagerFraSpleis, sykepengedagerEtterRequest)

        sykepengedagerTopic.assertThat()
            .hasNumberOfRecordsForKey("123", 2)
            .hasValuesForPredicate("123", 2) { it.response != null }
            .hasLastValueMatching { assertEquals(sykepengedagerFraSpleis, it) }
    }

    @Test
    fun `Mottatt behov fører til response selv om bruker ikke har sykepengedager`() {
        val sykepengedagerTopic = kafka.testTopic(Topics.sykepengedager)
        val subscriberTopic = kafka.testTopic(Topics.subscribe)

        subscriberTopic.produce("123") {
            "".toByteArray()
        }

        sykepengedagerTopic.assertThat()
            .hasNumberOfRecordsForKey("123", 1)
            .hasValuesForPredicate("123", 1) { it.response != null }
            .hasLastValueMatching { assertNull(requireNotNull(it?.response).sykepengedager) }
    }
}
