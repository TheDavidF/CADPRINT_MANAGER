package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import gt.cadprint.manager.QuotationDtos.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class QuotationRestartTest {
    @Test
    void readsTheSameCommercialSnapshotAfterRestart(@TempDir Path directory) {
        String url = "jdbc:h2:file:" + directory.resolve("quotation-restart");
        long id;
        CommercialView before;
        byte[] original;
        try (var context = start(url, directory, "CADPRINT original")) {
            var service = context.getBean(QuotationService.class);
            var line = new LineInput(QuotationLineType.CUSTOM, null, null, null, "Taza", "Personalizada",
                    BigDecimal.valueOf(2), new BigDecimal("15.25"), null);
            var draft = new DraftInput(new Recipient("Destinatario", null, null, null, null),
                    "Entrega en tienda", "Nota interna", null, null, BigDecimal.ZERO, List.of(line));
            var created = service.create(draft, "operador");
            id = created.revision().quotationId();
            service.emit(id, 1, new VersionInput(created.revision().version()), "operador");
            before = service.commercial(id, 1);
            context.getBean(QuotationDocumentProcessor.class).processDue();
            original = context.getBean(QuotationDocumentService.class).download(id, 1).bytes();
        }

        try (var context = start(url + ";IFEXISTS=TRUE", directory, "Otro perfil de emisor")) {
            var service = context.getBean(QuotationService.class);
            assertThat(service.commercial(id, 1)).isEqualTo(before);
            assertThat(context.getBean(QuotationDocumentService.class).download(id, 1).bytes()).isEqualTo(original);
            assertThat(service.events(id)).extracting(EventView::type)
                    .containsExactly(QuotationEventType.CREATED, QuotationEventType.ISSUED);
        }
    }

    private ConfigurableApplicationContext start(String url, Path directory, String issuer) {
        return new SpringApplicationBuilder(CadprintApplication.class).run(
                "--cadprint.quotation-documents.worker-enabled=false",
                "--cadprint.quotation-documents.directory=" + directory.resolve("pdfs"),
                "--server.port=0", "--spring.datasource.url=" + url,
                "--cadprint.quotations.issuer-name=" + issuer,
                "--CADPRINT_ADMIN_PASSWORD=test-admin-password",
                "--CADPRINT_OPERATOR_PASSWORD=test-operator-password");
    }
}
