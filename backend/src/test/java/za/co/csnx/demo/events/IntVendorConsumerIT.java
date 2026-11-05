package za.co.csnx.demo.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import za.co.csnx.demo.TestcontainersConfiguration;
import za.co.csnx.engine.events.EventEnvelope;

/**
 * DEMO's first inbound consumer: {@code pom.vendor.changed} (pom WAL CDC) →
 * {@code demo.int_vendor}. Invokes the {@code vendorEvents} bean directly
 * (bypassing the broker — pom's {@code PlatformUserMirrorConsumerIT}
 * pattern): I upserts, U updates, D tombstones retaining the last-known
 * fields, the inbox dedups a re-delivered event id, and foreign event types
 * are ignored. Also proves the engine event tables (V9000+) now land in the
 * demo schema via classpath:db/engine.
 *
 * <p>{@code spring.cloud.function.definition} is blanked so Spring Cloud
 * Stream doesn't try to bind to an absent broker during the test.
 */
@SpringBootTest(properties = {
        "csnx.events.enabled=true",
        "csnx.events.relay.poll-ms=600000",
        "spring.cloud.function.definition="
})
@Import(TestcontainersConfiguration.class)
class IntVendorConsumerIT {

    @Autowired
    @Qualifier("integrationEvents")
    private Consumer<Message<byte[]>> consumer;

    @Autowired private NamedParameterJdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private MeterRegistry meters;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM demo.int_vendor", new MapSqlParameterSource());
        jdbc.update("DELETE FROM demo.event_inbox WHERE consumer = :c",
                new MapSqlParameterSource("c", DemoEventConsumers.VENDOR_CONSUMER_ID));
    }

    @Test
    void insertUpdateDeleteLifecycleWithTombstoneAndDedup() {
        deliver(vendorEvent(UUID.randomUUID(), "I", "VND-901", "Test Vendor", "Cape Town", true));
        Map<String, Object> inserted = mirrorRow("VND-901");
        assertThat(inserted).containsEntry("vendor_name", "Test Vendor")
                .containsEntry("last_op", "I")
                .containsEntry("is_deleted", false);

        // Redelivered event id — inbox dedups, no re-apply.
        UUID updateId = UUID.randomUUID();
        deliver(vendorEvent(updateId, "U", "VND-901", "Test Vendor Renamed", "Cape Town", false));
        deliver(vendorEvent(updateId, "U", "VND-901", "SHOULD NOT WIN", "Cape Town", true));
        Map<String, Object> updated = mirrorRow("VND-901");
        assertThat(updated).containsEntry("vendor_name", "Test Vendor Renamed")
                .containsEntry("active", false)
                .containsEntry("last_op", "U");
        assertThat(inboxCount()).isEqualTo(2);

        // Hard delete on pom → tombstone here, last-known fields retained.
        deliver(deleteEvent(UUID.randomUUID(), "VND-901"));
        Map<String, Object> tombstoned = mirrorRow("VND-901");
        assertThat(tombstoned).containsEntry("is_deleted", true)
                .containsEntry("last_op", "D")
                .containsEntry("vendor_name", "Test Vendor Renamed");

        // Foreign event type on the shared exchange — ignored entirely.
        deliver(new EventEnvelope(UUID.randomUUID(), "platform.user.upserted",
                "PLATFORM", "WCS/jdoe", Instant.now(), EventEnvelope.SPEC_VERSION,
                mapper.createObjectNode()));
        assertThat(inboxCount()).isEqualTo(3);
    }

    @Test
    void aFailingWriteRethrowsRollsBackTheInboxAndIsNotCountedAsRejected() {
        double rejectedBefore = meters.counter("demo.event.rejected").count();
        EventEnvelope noData = new EventEnvelope(UUID.randomUUID(), "pom.vendor.changed",
                "POM", "VND-902", Instant.now(), EventEnvelope.SPEC_VERSION, null);

        assertThatThrownBy(() -> deliver(noData)).isInstanceOf(RuntimeException.class);

        assertThat(inboxCount()).isZero();
        assertThat(meters.counter("demo.event.rejected").count()).isEqualTo(rejectedBefore);
    }

    private EventEnvelope vendorEvent(UUID id, String op, String code, String name,
                                      String city, Boolean active) {
        // Wire keys are pom.vendor's snake_case column names (wal2json image);
        // columns this mirror doesn't need ride along and are ignored.
        ObjectNode data = mapper.createObjectNode();
        data.put("op", op);
        data.put("vendor_code", code);
        data.put("vendor_name", name);
        data.put("city", city);
        data.put("country", "ZA");
        data.put("contact_email", "v@x.com");
        data.put("active", active);
        data.put("lead_time_group", "A");
        data.put("update_serial", 0);
        return new EventEnvelope(id, "pom.vendor.changed", "POM", code,
                Instant.now(), EventEnvelope.SPEC_VERSION, data);
    }

    private EventEnvelope deleteEvent(UUID id, String code) {
        ObjectNode data = mapper.createObjectNode();
        data.put("op", "D");
        data.put("vendor_code", code);   // PK-only old image (default replica identity)
        return new EventEnvelope(id, "pom.vendor.changed", "POM", code,
                Instant.now(), EventEnvelope.SPEC_VERSION, data);
    }

    private Map<String, Object> mirrorRow(String vendorCode) {
        return jdbc.queryForMap(
                "SELECT vendor_name, city, active, last_op, is_deleted FROM demo.int_vendor"
                        + " WHERE vendor_code = :code",
                new MapSqlParameterSource("code", vendorCode));
    }

    private int inboxCount() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM demo.event_inbox WHERE consumer = :c",
                new MapSqlParameterSource("c", DemoEventConsumers.VENDOR_CONSUMER_ID), Integer.class);
        return n == null ? 0 : n;
    }

    private void deliver(EventEnvelope event) {
        try {
            consumer.accept(MessageBuilder.withPayload(mapper.writeValueAsBytes(event)).build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
