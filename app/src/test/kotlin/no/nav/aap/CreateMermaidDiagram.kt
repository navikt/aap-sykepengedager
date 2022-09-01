package no.nav.aap

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.aap.app.topology
import no.nav.aap.dto.kafka.SykepengedagerKafkaDto
import no.nav.aap.kafka.streams.topology.Mermaid
import org.apache.kafka.clients.producer.MockProducer
import org.junit.jupiter.api.Test
import java.io.File

internal class CreateMermaidDiagram {
    @Test
    fun `generate mermaid diagram`() {
        val sykepengedagerProducer = MockProducer<String, SykepengedagerKafkaDto>()
        val topology = topology(SimpleMeterRegistry(), sykepengedagerProducer)
        val flowchart = Mermaid.graph("Sykepengedager", topology)
        val mermaidFlowcharMarkdown = markdown(flowchart)
        File("../doc/topology.md").apply { writeText(mermaidFlowcharMarkdown) }
        File("../doc/topology.mermaid").apply { writeText(flowchart) }
    }
}

fun markdown(mermaid: String) = """
```mermaid
$mermaid
```
"""
