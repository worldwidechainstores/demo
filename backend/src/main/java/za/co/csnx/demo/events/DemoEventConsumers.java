package za.co.csnx.demo.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import za.co.csnx.engine.events.EventEnvelope;
import za.co.csnx.engine.events.IdempotentEventHandler;

/**
 * DEMO's inbound integration events (Channel 2). A single {@code Consumer} bean
 * bound in application.yml as {@code integrationEvents-in-0} → destination
 * {@code csnx.events}, durable group {@code demo}; it dispatches by event type,
 * ignoring the foreign types that also land on the shared exchange.
 *
 * <p>Slices:
 * <ul>
 *   <li>{@code pom.vendor.changed} — pom's vendor master → {@code demo.int_vendor}.</li>
 *   <li>{@code platform.company.changed} — the platform's company master →
 *       {@code demo.int_company} (the platform → demo leg of the CDC triangle).</li>
 * </ul>
 *
 * <p>The bean takes the raw message and parses the CloudEvents JSON itself: legacy CSnx publishes over
 * Qpid JMS, which sends a BytesMessage as {@code application/octet-stream}, so a typed
 * {@code Consumer<EventEnvelope>} fails the conversion with a {@link ClassCastException} on every CSnx
 * event. Platform modules send {@code application/json} through {@code StreamBridge}; both arrive as bytes
 * and parse the same. A body that is not an envelope with an id is logged, counted in
 * {@code demo.event.rejected} and dropped: it can never succeed, so retrying it only delays the same
 * discard (the binding has no dead-letter queue), and it must not hold up the events behind it. A failure
 * inside the business write is not caught here, so it still rolls back the inbox row and rethrows.
 *
 * <p>Both slices are WAL-CDC captured (configured centrally on the platform's CDCC screen).
 * Delivery is at-least-once: the inbox dedups broker redelivery, and the mirror
 * writes are state-based so CDC-level replays (fresh event ids) converge too.
 * Flow walkthrough: {@code docs/cdc-vendor-pom-to-demo.md} (platform repo).
 */
@Configuration
@ConditionalOnProperty(prefix = "csnx.events", name = "enabled", havingValue = "true")
public class DemoEventConsumers {

    private static final Logger log = LoggerFactory.getLogger(DemoEventConsumers.class);

    static final String VENDOR_CHANGED = "pom.vendor.changed";
    static final String COMPANY_CHANGED = "platform.company.changed";

    /** Stable ids for each subscriber in the inbox dedup ledger (one per slice). */
    static final String VENDOR_CONSUMER_ID = "demo:intVendorMirror";
    static final String COMPANY_CONSUMER_ID = "demo:intCompanyMirror";

    private final IdempotentEventHandler idempotent;
    private final IntVendorMirrorStore vendorMirror;
    private final IntCompanyMirrorStore companyMirror;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    public DemoEventConsumers(IdempotentEventHandler idempotent,
                              IntVendorMirrorStore vendorMirror,
                              IntCompanyMirrorStore companyMirror,
                              ObjectMapper mapper,
                              MeterRegistry meters) {
        this.idempotent = idempotent;
        this.vendorMirror = vendorMirror;
        this.companyMirror = companyMirror;
        this.mapper = mapper;
        this.meters = meters;
    }

    @Bean
    public Consumer<Message<byte[]>> integrationEvents() {
        return message -> {
            EventEnvelope event = parse(message.getPayload());
            if (event == null) {
                return;
            }
            String type = event.type();
            if (VENDOR_CHANGED.equals(type)) {
                idempotent.handle(event, VENDOR_CONSUMER_ID, e -> {
                    VendorChangeData change = e.dataAs(mapper, VendorChangeData.class);
                    vendorMirror.apply(change, e.time());
                    log.info("Mirrored vendor {} (op {}, event {})", e.subject(), change.op(), e.id());
                });
            } else if (COMPANY_CHANGED.equals(type)) {
                idempotent.handle(event, COMPANY_CONSUMER_ID, e -> {
                    CompanyChangeData change = e.dataAs(mapper, CompanyChangeData.class);
                    companyMirror.apply(change, e.time());
                    log.info("Mirrored company {} (op {}, event {})", e.subject(), change.op(), e.id());
                });
            }
            // other event types on the shared exchange — not ours
        };
    }

    private EventEnvelope parse(byte[] payload) {
        try {
            EventEnvelope event = mapper.readValue(payload, EventEnvelope.class);
            if (event == null || event.id() == null) {
                // Jackson accepts a body without an id; the inbox dedup key needs one.
                throw new IOException("no id");
            }
            return event;
        } catch (IOException | RuntimeException e) {
            meters.counter("demo.event.rejected").increment();
            log.warn("Dropping an unreadable event ({} bytes): {}", payload == null ? 0 : payload.length,
                    e.getMessage());
            return null;
        }
    }
}
