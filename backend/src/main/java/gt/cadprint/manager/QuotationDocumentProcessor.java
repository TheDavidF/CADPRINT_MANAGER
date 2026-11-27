package gt.cadprint.manager;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class QuotationDocumentProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(QuotationDocumentProcessor.class);
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;

    private final QuotationDocumentQueue queue;
    private final QuotationService quotations;
    private final QuotationPdfRenderer renderer;
    private final QuotationDocumentStorage storage;

    QuotationDocumentProcessor(QuotationDocumentQueue queue, QuotationService quotations,
                               QuotationPdfRenderer renderer, QuotationDocumentStorage storage) {
        this.queue = queue;
        this.quotations = quotations;
        this.renderer = renderer;
        this.storage = storage;
    }

    void processDue() {
        queue.due(Instant.now()).forEach(this::process);
    }

    void process(long id) {
        Instant now = Instant.now();
        String token = UUID.randomUUID().toString();
        if (!queue.claim(id, token, now, now.plus(LEASE))) {
            return;
        }
        var job = queue.find(id).orElseThrow();
        if (job.status() != QuotationDocumentQueue.Status.PROCESSING) {
            return;
        }

        try {
            var candidate = job.candidate();
            if (candidate == null || !storage.recover(candidate)) {
                var commercial = quotations.commercialByRevisionId(id);
                candidate = storage.stage(renderer.render(commercial, job.templateVersion()));
                if (!queue.stage(id, token, candidate)) {
                    return;
                }
                if (!storage.recover(candidate)) {
                    throw new IOException("No se pudo verificar el PDF publicado");
                }
            }
            queue.ready(id, token, Instant.now());
        } catch (IOException | RuntimeException failure) {
            String code = failure instanceof IOException ? "DOCUMENT_IO" : "DOCUMENT_PROCESSING";
            LOG.warn("Quotation document {} attempt {} failed ({})", id, job.attempts(), code);
            Instant retry = job.attempts() < MAX_ATTEMPTS
                    ? Instant.now().plusSeconds(15L << (job.attempts() - 1)) : null;
            queue.fail(id, token, retry, code);
        }
    }
}
