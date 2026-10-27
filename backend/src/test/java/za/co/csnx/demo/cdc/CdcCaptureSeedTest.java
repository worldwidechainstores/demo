package za.co.csnx.demo.cdc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import za.co.csnx.demo.TestcontainersConfiguration;

/**
 * DEMO's own capture config is seeded into its own schema (CSNX-14756).
 *
 * <p>Until Phase 2 this module fetched its capture set from the platform over
 * Channel 1 every 60 seconds; it now reads {@code demo.int_cdc_capture}, so the
 * config sits in the same schema as its own run state and CDC no longer depends on
 * the platform being reachable.
 *
 * <p>Container-backed, via the shared {@link TestcontainersConfiguration}, because
 * the point is that the MIGRATION produces this row — a slice test against a
 * developer's local database would assert whatever happened to be sitting there.
 *
 * <p>Named {@code ...Test}, not {@code ...IT}, deliberately: this module keeps
 * surefire's default includes, so a class ending in IT is never run by {@code mvn test}
 * — its own {@code IntVendorConsumerIT} does not run either. The container-backed
 * tests that DO run here are named Test/Tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CdcCaptureSeedTest {

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Test
    void demoSeedsItsOwnTraderCaptureAndNobodyElses() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT module_cd, config_name, enabled, source_table, event_type,"
                        + " subject_columns, created_at, updated_at"
                        + " FROM demo.int_cdc_capture ORDER BY config_name",
                new MapSqlParameterSource());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("module_cd", "DEMO")
                .containsEntry("config_name", "demo-trader")
                .containsEntry("enabled", true)
                .containsEntry("source_table", "demo.scwt_trader")
                .containsEntry("event_type", "demo.trader.changed")
                // The key int_cdc_state joins on. Renaming the capture would
                // re-backfill the whole trader table as upserts.
                .containsEntry("subject_columns", "cpy_cd,trader_type,trader_code");
        // The engine band declares these NOT NULL with no column DEFAULT — JPA
        // auditing stamps them, so a Flyway seed has to supply its own.
        assertThat(rows.get(0).get("created_at")).isNotNull();
        assertThat(rows.get(0).get("updated_at")).isNotNull();
    }
}
