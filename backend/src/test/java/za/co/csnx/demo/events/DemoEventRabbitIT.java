package za.co.csnx.demo.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Import;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import za.co.csnx.demo.TestcontainersConfiguration;
import za.co.csnx.engine.events.EventEnvelope;

/**
 * The real-broker round-trip for both senders on the shared exchange: platform modules publish an
 * {@link EventEnvelope} through {@code StreamBridge} ({@code application/json}), and legacy CSnx
 * publishes the same JSON as an AMQP bytes body that Qpid JMS tags {@code application/octet-stream}.
 * Both must reach the mirror; a body that is not an envelope must be dropped without stopping the
 * events behind it.
 */
@SpringBootTest(properties = "csnx.events.enabled=true")
@Import(TestcontainersConfiguration.class)
@Testcontainers
class DemoEventRabbitIT {

    @Container
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"));

    @DynamicPropertySource
    static void rabbitProps(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired private StreamBridge streamBridge;
    @Autowired private NamedParameterJdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private MeterRegistry meters;

    @BeforeEach
    @AfterEach
    void purgeOwnRows() {
        jdbc.update("DELETE FROM demo.int_vendor WHERE vendor_code LIKE 'RBT-%'", new MapSqlParameterSource());
        jdbc.update("DELETE FROM demo.event_inbox WHERE consumer = :c",
                new MapSqlParameterSource("c", DemoEventConsumers.VENDOR_CONSUMER_ID));
    }

    @Test
    void eventPublishedThroughTheBinderIsConsumedAndMirrored() throws Exception {
        streamBridge.send("csnx.events", vendorEvent("RBT-JSON", "Json Vendor"));

        assertThat(awaitVendorName("RBT-JSON")).isEqualTo("Json Vendor");
    }

    @Test
    void octetStreamBodyFromLegacyCsnxIsConsumedAndMirrored() throws Exception {
        publishRaw(mapper.writeValueAsBytes(vendorEvent("RBT-OCTET", "Octet Vendor")),
                "application/octet-stream");

        assertThat(awaitVendorName("RBT-OCTET")).isEqualTo("Octet Vendor");
    }

    @Test
    void bodyThatIsNotAnEnvelopeIsDroppedAndTheNextEventStillArrives() throws Exception {
        double rejectedBefore = meters.counter("demo.event.rejected").count();
        publishRaw("not json".getBytes(StandardCharsets.UTF_8), "application/octet-stream");
        publishRaw("{\"type\":\"pom.vendor.changed\"}".getBytes(StandardCharsets.UTF_8),
                "application/octet-stream");
        publishRaw(mapper.writeValueAsBytes(vendorEvent("RBT-AFTER", "After Garbage")),
                "application/octet-stream");

        assertThat(awaitVendorName("RBT-AFTER")).isEqualTo("After Garbage");
        assertThat(meters.counter("demo.event.rejected").count() - rejectedBefore).isEqualTo(2.0);
    }

    private EventEnvelope vendorEvent(String code, String name) {
        ObjectNode data = mapper.createObjectNode();
        data.put("op", "I");
        data.put("vendor_code", code);
        data.put("vendor_name", name);
        data.put("city", "Cape Town");
        data.put("country", "ZA");
        data.put("active", true);
        return new EventEnvelope(UUID.randomUUID(), "pom.vendor.changed", "POM", code,
                Instant.now(), EventEnvelope.SPEC_VERSION, data);
    }

    private void publishRaw(byte[] body, String contentType) {
        CachingConnectionFactory connections = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connections.setUsername(RABBIT.getAdminUsername());
        connections.setPassword(RABBIT.getAdminPassword());
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(contentType);
            new RabbitTemplate(connections).send("csnx.events", "csnx.legacy", new Message(body, properties));
        } finally {
            connections.destroy();
        }
    }

    private String awaitVendorName(String code) throws InterruptedException {
        String name = null;
        for (int i = 0; i < 40 && name == null; i++) {
            name = vendorNameOrNull(code);
            if (name == null) {
                Thread.sleep(500);
            }
        }
        return name;
    }

    private String vendorNameOrNull(String code) {
        try {
            return jdbc.queryForObject("SELECT vendor_name FROM demo.int_vendor WHERE vendor_code = :code",
                    new MapSqlParameterSource("code", code), String.class);
        } catch (EmptyResultDataAccessException notYet) {
            return null;
        }
    }
}
