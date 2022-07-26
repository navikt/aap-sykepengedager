package no.nav.aap.app

import io.ktor.server.testing.*
import no.nav.aap.app.kafka.SØKERE_STORE_NAME
import no.nav.aap.app.kafka.Topics
import no.nav.aap.app.modell.*
import no.nav.aap.app.modell.InntekterKafkaDto.Response.Inntekt
import no.nav.aap.dto.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.YearMonth
import no.nav.aap.avro.medlem.v1.ErMedlem as AvroErMedlem
import no.nav.aap.avro.medlem.v1.Response as AvroMedlemResponse

internal class ApiTest {

    @Test
    fun `søker får innvilget sykepengedager`() {
        withTestApp { mocks ->
            val inntektTopic = mocks.kafka.inputTopic(Topics.inntekter)
            val inntektOutputTopic = mocks.kafka.outputTopic(Topics.inntekter)
            val stateStore = mocks.kafka.getStore<SøkereKafkaDto>(SØKERE_STORE_NAME)

            val fnr = "123"
            val inntekter: InntekterKafkaDto = inntektOutputTopic.readValue()
            inntektTopic.produce(fnr) {
                inntekter.copy(
                    response = InntekterKafkaDto.Response(
                        listOf(
                            Inntekt("321", inntekter.request.fom.plusYears(2), 400000.0),
                            Inntekt("321", inntekter.request.fom.plusYears(1), 400000.0),
                            Inntekt("321", inntekter.request.fom, 400000.0),
                        )
                    )
                )
            }

            val søker = stateStore[fnr]
            assertNotNull(søker)
            val actual = søker.toDto()
            assertNotNull(actual.saker.firstOrNull()?.sykepengedager) { "Saken mangler sykepengedager - $actual" }
            val søknadstidspunkt = actual.saker.first().søknadstidspunkt

            fun vilkårsvurderingsid(index: Int) =
                actual.saker.first().sakstyper.first().vilkårsvurderinger[index].vilkårsvurderingsid

            assertEquals(expected, actual)
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
