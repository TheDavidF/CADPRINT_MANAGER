package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class QuotationDocumentMigrationTest {
    @Test
    void upgradesAnIsolatedV8CopyWithoutBackfillingDocuments(@TempDir Path directory) throws Exception {
        Path reference = directory.resolve("reference-v8");
        String referenceUrl = "jdbc:h2:file:" + reference;
        Flyway.configure().dataSource(referenceUrl, "sa", "")
                .locations("classpath:db/migration/h2", "classpath:db/migration/common")
                .target("8").load().migrate();
        var old = new JdbcTemplate(new DriverManagerDataSource(referenceUrl, "sa", ""));
        old.update("""
                INSERT INTO quotations(id, quotation_number, created_by, created_at, updated_at)
                VALUES (1, 'COT-000001', 'operador', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        old.update("""
                INSERT INTO quotation_revisions(id, quotation_id, revision_number, status, currency,
                    recipient_name, issuer_name, issued_at, valid_until, general_discount_type, general_discount_amount,
                    shipping_amount, gross_amount, line_discount_total, subtotal, total_amount,
                    calculation_policy_version, created_at, updated_at)
                VALUES (1, 1, 1, 'EMITIDA', 'GTQ', 'Cliente histórico', 'CADPRINT', CURRENT_TIMESTAMP, CURRENT_DATE,
                    'NONE', 0, 0, 0, 0, 0, 0, 'v1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        var snapshot = old.queryForList("SELECT * FROM quotation_revisions");
        Path copy = directory.resolve("copy-v9");
        Files.copy(Path.of(reference + ".mv.db"), Path.of(copy + ".mv.db"));

        try (var context = new SpringApplicationBuilder(CadprintApplication.class).run(
                "--server.port=0", "--spring.datasource.url=jdbc:h2:file:" + copy + ";IFEXISTS=TRUE",
                "--cadprint.quotation-documents.worker-enabled=false",
                "--cadprint.quotation-documents.directory=" + directory.resolve("pdfs"),
                "--CADPRINT_ADMIN_PASSWORD=test-admin-password", "--CADPRINT_OPERATOR_PASSWORD=test-operator-password")) {
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(context.getBean(Flyway.class).info().current().getVersion().getVersion()).isEqualTo("17");
            var jdbc = context.getBean(JdbcTemplate.class);
            var migrated = jdbc.queryForList("SELECT * FROM quotation_revisions");
            assertThat(migrated).allSatisfy(row -> {
                assertThat(row).containsEntry("CUSTOMER_ID", null);
                row.remove("CUSTOMER_ID");
            });
            assertThat(migrated).isEqualTo(snapshot);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quotation_documents", Integer.class)).isZero();
            assertThat(context.getBean(QuotationDocumentService.class).request(1, 1).status())
                    .isEqualTo(QuotationDocumentQueue.Status.PENDING);
        }
        assertThat(old.queryForList("SELECT * FROM quotation_revisions")).isEqualTo(snapshot);
        assertThat(old.queryForObject("""
                SELECT COUNT(*) FROM "flyway_schema_history" WHERE "version" = '9'
                """, Integer.class)).isZero();
    }
}
