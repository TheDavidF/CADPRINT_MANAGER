package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "CADPRINT_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
class MysqlMigrationTest {
    @Test
    void upgradesExistingQuotationsOrdersPaymentsAndCosts() {
        var dataSource = new DriverManagerDataSource(System.getenv("CADPRINT_TEST_MYSQL_URL"),
                System.getenv("CADPRINT_TEST_MYSQL_USER"), System.getenv("CADPRINT_TEST_MYSQL_PASSWORD"));
        var jdbc = new JdbcTemplate(dataSource);
        migrate(dataSource, "9");
        jdbc.update("""
                INSERT INTO quotations (id, quotation_number, created_by, created_at, updated_at)
                VALUES (1, 'COT-000001', 'operador', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO quotation_revisions (id, quotation_id, revision_number, status, currency,
                    recipient_name, recipient_email, issuer_name, issued_at, valid_until, general_discount_type,
                    general_discount_amount, shipping_amount, gross_amount, line_discount_total, subtotal,
                    total_amount, calculation_policy_version, created_at, updated_at)
                VALUES (1, 1, 1, 'ACEPTADA', 'GTQ', 'Cliente histórico', 'audit@example.test',
                    'CADPRINT', CURRENT_TIMESTAMP, CURRENT_DATE, 'NONE', 0, 0, 25, 0, 25, 25, 'v1',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        migrate(dataSource, "11");
        assertThat(jdbc.queryForObject("SELECT customer_id FROM quotation_revisions WHERE id = 1", Long.class)).isNull();
        jdbc.update("""
                INSERT INTO customer_orders (id, quotation_id, revision_number, created_at, created_by)
                VALUES (1, 1, 1, CURRENT_TIMESTAMP, 'operador')
                """);
        migrate(dataSource, "14");
        recordPaymentAndProduction(jdbc);

        migrate(dataSource, "17");
        jdbc.update("""
                INSERT INTO order_direct_costs (customer_order_id, request_id, category, description,
                    amount, incurred_on, recorded_at, recorded_by)
                VALUES (1, '31dc2315-4833-4bbc-a7c8-c34074f4abbc', 'ENVIO', 'Mensajería',
                    6.50, CURRENT_DATE, CURRENT_TIMESTAMP, 'admin')
                """);

        migrate(dataSource, "18");
        var flyway = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration/mysql", "classpath:db/migration/common").load();
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT recipient_email FROM quotation_revisions WHERE id = 1", String.class))
                .isEqualTo("audit@example.test");
        assertThat(jdbc.queryForObject("SELECT total_amount FROM quotation_revisions WHERE id = 1", BigDecimal.class))
                .isEqualByComparingTo("25.00");
        assertThat(jdbc.queryForObject("SELECT production_started_by FROM customer_orders WHERE id = 1", String.class))
                .isEqualTo("operador");
        assertThat(jdbc.queryForObject("SELECT costs_confirmed_by FROM customer_orders WHERE id = 1", String.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT amount FROM order_payments WHERE customer_order_id = 1", BigDecimal.class))
                .isEqualByComparingTo("10.00");
        assertThat(jdbc.queryForObject("SELECT amount FROM order_direct_costs WHERE customer_order_id = 1", BigDecimal.class))
                .isEqualByComparingTo("6.50");
    }

    private void recordPaymentAndProduction(JdbcTemplate jdbc) {
        jdbc.update("""
                UPDATE customer_orders SET production_started_at = CURRENT_TIMESTAMP, production_started_by = 'operador'
                WHERE id = 1
                """);
        jdbc.update("""
                INSERT INTO order_payments (customer_order_id, request_id, amount, method, received_at, received_by)
                VALUES (1, '66434726-ecb4-40ea-a384-747da6358493', 10.00, 'EFECTIVO', CURRENT_TIMESTAMP, 'operador')
                """);
    }

    private void migrate(DriverManagerDataSource dataSource, String version) {
        var flyway = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration/mysql", "classpath:db/migration/common")
                .target(version).load();
        flyway.migrate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(version);
    }
}
