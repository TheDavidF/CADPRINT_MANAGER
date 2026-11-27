package gt.cadprint.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import gt.cadprint.manager.QuotationDtos.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:quotation-docs-${random.uuid}",
        "cadprint.quotation-documents.worker-enabled=false",
        "CADPRINT_ADMIN_PASSWORD=test-admin-password",
        "CADPRINT_OPERATOR_PASSWORD=test-operator-password"
})
@AutoConfigureMockMvc
class QuotationDocumentIntegrationTest {
    private static final RequestPostProcessor OPERATOR = httpBasic("operador", "test-operator-password");
    private static final RequestPostProcessor ADMIN = httpBasic("admin", "test-admin-password");

    @TempDir static Path directory;

    @DynamicPropertySource
    static void storageDirectory(DynamicPropertyRegistry properties) {
        properties.add("cadprint.quotation-documents.directory", () -> directory.resolve("private").toString());
    }

    @Autowired QuotationService quotations;
    @Autowired QuotationDocumentQueue queue;
    @Autowired QuotationDocumentProcessor worker;
    @Autowired QuotationDocumentStorage storage;
    @Autowired QuotationPdfRenderer renderer;
    @Autowired QuotationRevisionRepo revisions;
    @Autowired ProductRepo products;
    @Autowired ProductCategoryRepo categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc api;

    @Test
    void generatesAnAuthenticatedMultipageOriginalWithExactCommercialContent() throws Exception {
        var draft = quotations.create(draft("María Muñoz", "¡Descripción especial! " +
                "Válido para impresión en color. ".repeat(75) + " Final único."), "operador");
        api.perform(get(path(draft)).with(OPERATOR)).andExpect(status().isConflict());
        api.perform(post(path(draft)).with(OPERATOR)).andExpect(status().isConflict());
        var issued = issue(draft);
        long revisionId = revisionId(issued);
        assertThat(queue.find(revisionId).orElseThrow().status()).isEqualTo(QuotationDocumentQueue.Status.PENDING);
        issue(draft);
        api.perform(post(path(issued)).with(OPERATOR)).andExpect(status().isAccepted());
        api.perform(get(path(issued) + "/download").with(OPERATOR)).andExpect(status().isConflict());
        api.perform(get(path(issued))).andExpect(status().isUnauthorized());
        api.perform(get(path(issued) + "/download").with(user("lector").roles("LECTOR")))
                .andExpect(status().isForbidden());
        var inventory = jdbc.queryForList("SELECT id, stock, unit_cost FROM materials ORDER BY id");
        var movementCount = jdbc.queryForObject("SELECT COUNT(*) FROM movements", Long.class);
        worker.process(revisionId);
        byte[] pdf = download(issued);
        assertThat(jdbc.queryForList("SELECT id, stock, unit_cost FROM materials ORDER BY id")).isEqualTo(inventory);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movements", Long.class)).isEqualTo(movementCount);
        api.perform(get(path(issued)).with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.sha256").value(QuotationDocumentStorage.hash(pdf)))
                .andExpect(jsonPath("$.sizeBytes").value(pdf.length))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
        api.perform(post(path(issued)).with(OPERATOR)).andExpect(status().isOk());
        assertThat(quotations.events(id(issued))).extracting(EventView::type)
                .containsExactly(QuotationEventType.CREATED, QuotationEventType.ISSUED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quotation_documents WHERE revision_id = ?",
                Integer.class, revisionId)).isOne();

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            var extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            int headers = 0;
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                assertThat(reader.getPageSize(page).getWidth()).isEqualTo(PageSize.A4.getWidth());
                assertThat(reader.getPageSize(page).getHeight()).isEqualTo(PageSize.A4.getHeight());
                String pageText = extractor.getTextFromPage(page);
                assertThat(pageText).contains("Página " + page);
                if (pageText.contains("Precio unit.")) {
                    headers++;
                }
                text.append(pageText);
            }
            assertThat(headers).isGreaterThanOrEqualTo(2);
            assertThat(text.toString()).contains("María Muñoz", "¡Descripción especial!", "Final único.",
                    "Descuento general", "Cargo de envío", "Total general (GTQ)", "Q 11.00",
                    "Q 2.00", "Q -0.50", "Q 1.00", "Q 9.50", "Condiciones comerciales", "Válido en Guatemala")
                    .doesNotContain("SECRETO-INTERNO", "productionCalculationId", "IVA");
        } finally {
            reader.close();
        }
    }

    @Test
    void keepsHistoricalBytesAcrossReplacementAndCommercialStateChanges() throws Exception {
        var first = issue(quotations.create(draft("Primera", "Descripción"), "operador"));
        worker.process(revisionId(first));
        byte[] original = download(first);
        var rejected = quotations.decide(id(first), 1, QuotationStatus.RECHAZADA, "operador", null);
        var second = quotations.revise(id(first), 1, new VersionInput(rejected.revision().version()), "operador");
        var replacement = issue(second);
        worker.process(revisionId(replacement));
        assertThat(download(first)).isEqualTo(original);
        assertThat(download(replacement)).isNotEqualTo(original);
    }

    @Test
    void preservesReadyBytesAfterACatalogEditAndRendererReplacement() throws Exception {
        var category = new ProductCategory();
        category.name = "Prueba PDF";
        var product = new Product("PDF-CATALOG");
        product.name = "Producto original";
        product.category = categories.saveAndFlush(category);
        product.productionMode = ProductionMode.BAJO_PEDIDO;
        product.pricingMode = PricingMode.PRECIO_BASE;
        product.basePrice = new BigDecimal("35.00");
        long productId = products.saveAndFlush(product).id;

        var line = new LineInput(QuotationLineType.CATALOG, productId, null, null, null, null,
                BigDecimal.ONE, null, null);
        var draft = new DraftInput(new Recipient("Cliente catálogo", null, null, null, null),
                null, null, null, null, null, List.of(line));
        var issued = issue(quotations.create(draft, "admin"));
        long revisionId = revisionId(issued);
        worker.process(revisionId);
        byte[] original = download(issued);
        assertThat(products.findById(productId).orElseThrow().basePrice).isEqualByComparingTo("35.00");

        product.name = "Nombre posterior";
        product.basePrice = new BigDecimal("80.00");
        products.saveAndFlush(product);
        var replacementRenderer = new QuotationPdfRenderer() {
            @Override
            byte[] render(CommercialView view, String version) {
                throw new AssertionError("El original READY no debe volver a renderizarse");
            }
        };
        new QuotationDocumentProcessor(queue, quotations, replacementRenderer, storage).process(revisionId);
        assertThat(download(issued)).isEqualTo(original);
    }

    @Test
    void recoversAnAbandonedStagedCandidateAndCoordinatesConcurrentWorkers() throws Exception {
        var issued = issue(quotations.create(draft("Recuperación", "Pieza"), "operador"));
        long revisionId = revisionId(issued);
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        String token = UUID.randomUUID().toString();
        assertThat(queue.claim(revisionId, token, Instant.now(), yesterday)).isTrue();
        byte[] original = renderer.render(quotations.commercial(id(issued), 1), QuotationPdfRenderer.VERSION);
        var candidate = storage.stage(original);
        assertThat(queue.stage(revisionId, token, candidate)).isTrue();
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); worker.process(revisionId); return null; });
            var second = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); worker.process(revisionId); return null; });
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        var ready = queue.find(revisionId).orElseThrow();
        assertThat(ready.attempts()).isEqualTo(2);
        assertThat(ready.candidate()).isEqualTo(candidate);
        assertThat(download(issued)).isEqualTo(original);
    }

    @Test
    void reportsCorruptionAndMissingOriginalWithoutReplacingIt() throws Exception {
        var issued = issue(quotations.create(draft("Integridad", "Taza"), "operador"));
        long revisionId = revisionId(issued);
        worker.process(revisionId);
        var original = queue.find(revisionId).orElseThrow();
        Path file = directory.resolve("private").resolve(original.candidate().key() + ".pdf");
        Files.writeString(file, "Corrupto");
        api.perform(get(path(issued) + "/download").with(OPERATOR)).andExpect(status().isServiceUnavailable());
        Files.delete(file);
        api.perform(get(path(issued) + "/download").with(OPERATOR)).andExpect(status().isServiceUnavailable());
        api.perform(post(path(issued)).with(OPERATOR)).andExpect(status().isOk());
        worker.process(revisionId);
        assertThat(queue.find(revisionId).orElseThrow()).isEqualTo(original);
        assertThat(file).doesNotExist();
    }

    @Test
    void retriesAStorageFailureWithoutReissuingTheQuotation(@TempDir Path temporary) throws Exception {
        var issued = issue(quotations.create(draft("Disco", "Pieza"), "operador"));
        long revisionId = revisionId(issued);
        Path brokenRoot = Files.createFile(temporary.resolve("pdfs"));
        var failing = new QuotationDocumentProcessor(queue, quotations, renderer,
                new QuotationDocumentStorage(brokenRoot.toString()));
        failing.process(revisionId);
        var pending = queue.find(revisionId).orElseThrow();
        assertThat(pending.status()).isEqualTo(QuotationDocumentQueue.Status.PENDING);
        assertThat(pending.error()).isEqualTo("DOCUMENT_IO");
        assertThat(quotations.detail(id(issued), 1).revision().status()).isEqualTo(QuotationStatus.EMITIDA);
        jdbc.update("UPDATE quotation_documents SET next_attempt_at = ? WHERE revision_id = ?",
                Instant.now().minusSeconds(10).atOffset(java.time.ZoneOffset.UTC), revisionId);
        worker.process(revisionId);
        assertThat(download(issued)).isNotEmpty();
        assertThat(quotations.events(id(issued))).extracting(EventView::type)
                .containsExactly(QuotationEventType.CREATED, QuotationEventType.ISSUED);
    }

    @Test
    void rollsBackEmissionIfEnqueueFailsAndCanEnqueueALegacyRevision() throws Exception {
        var draft = quotations.create(draft("Transacción", "Taza"), "operador");
        long revisionId = revisionId(draft);
        queue.enqueue(revisionId, Instant.now());
        api.perform(post(path(draft).replace("/document", "/emit")).with(OPERATOR)
                .contentType("application/json").content("{\"version\":" + draft.revision().version() + "}"))
                .andExpect(status().isConflict());
        var persisted = quotations.detail(id(draft), 1);
        assertThat(persisted.revision().status()).isEqualTo(QuotationStatus.BORRADOR);
        assertThat(persisted.revision().quotationNumber()).isNull();
        assertThat(persisted.revision().issuedAt()).isNull();
        assertThat(quotations.events(id(draft))).extracting(EventView::type).containsExactly(QuotationEventType.CREATED);

        jdbc.update("DELETE FROM quotation_documents WHERE revision_id = ?", revisionId);
        var issued = issue(draft);
        jdbc.update("DELETE FROM quotation_documents WHERE revision_id = ?", revisionId);
        api.perform(get(path(issued)).with(OPERATOR)).andExpect(status().isNotFound());
        api.perform(post(path(issued)).with(OPERATOR)).andExpect(status().isAccepted());
        worker.process(revisionId);
        assertThat(download(issued)).isNotEmpty();
        assertThat(quotations.events(id(issued))).extracting(EventView::type)
                .containsExactly(QuotationEventType.CREATED, QuotationEventType.ISSUED);
    }

    private DraftInput draft(String recipient, String description) {
        var line = new LineInput(QuotationLineType.CUSTOM, null, null, null, "Artículo", description,
                BigDecimal.valueOf(2), new BigDecimal("5.50"),
                new Discount(QuotationDiscountType.FIXED, new BigDecimal("2.00")));
        return new DraftInput(new Recipient(recipient, null, null, null, null), "Válido en Guatemala",
                "SECRETO-INTERNO", null, new Discount(QuotationDiscountType.FIXED, new BigDecimal("0.50")),
                new BigDecimal("1.00"), List.of(line));
    }

    private Detail issue(Detail draft) {
        return quotations.emit(id(draft), draft.revision().revisionNumber(),
                new VersionInput(draft.revision().version()), "operador");
    }

    private long id(Detail detail) {
        return detail.revision().quotationId();
    }

    private long revisionId(Detail detail) {
        return revisions.findByQuotationIdAndRevisionNumber(id(detail), detail.revision().revisionNumber())
                .orElseThrow().id;
    }

    private String path(Detail detail) {
        return "/api/quotations/" + id(detail) + "/revisions/" + detail.revision().revisionNumber() + "/document";
    }

    private byte[] download(Detail detail) throws Exception {
        return api.perform(get(path(detail) + "/download").with(OPERATOR)).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("CADPRINT-COT-")))
                .andReturn().getResponse().getContentAsByteArray();
    }
}
