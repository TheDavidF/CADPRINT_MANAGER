package gt.cadprint.manager;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

import gt.cadprint.manager.QuotationDocumentQueue.Job;
import gt.cadprint.manager.QuotationDocumentQueue.Status;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class QuotationDocumentService {
    record Metadata(Status status, String templateVersion, String sha256, Long sizeBytes,
                    Instant generatedAt, String errorCode) {}
    record Download(String filename, byte[] bytes) {}

    private final QuotationRepo quotations;
    private final QuotationRevisionRepo revisions;
    private final QuotationDocumentQueue queue;
    private final QuotationDocumentStorage storage;

    QuotationDocumentService(QuotationRepo quotations, QuotationRevisionRepo revisions,
                             QuotationDocumentQueue queue, QuotationDocumentStorage storage) {
        this.quotations = quotations;
        this.revisions = revisions;
        this.queue = queue;
        this.storage = storage;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    Metadata request(long id, int number) {
        quotations.findByIdForUpdate(id).orElseThrow(() -> QuotationService.missing("Cotización no encontrada"));
        long revisionId = issued(id, number).id;
        Instant now = Instant.now();
        var existing = queue.find(revisionId);
        if (existing.isEmpty()) {
            queue.enqueue(revisionId, now);
        } else if (existing.get().status() == Status.FAILED) {
            queue.retry(revisionId, now);
        }
        return metadata(queue.find(revisionId).orElseThrow());
    }

    @Transactional(readOnly = true)
    Metadata status(long id, int number) {
        return metadata(job(issued(id, number).id));
    }

    Download download(long id, int number) {
        var revision = issued(id, number);
        var job = job(revision.id);
        if (job.status() != Status.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El documento aún no está disponible");
        }
        String commercialNumber = quotations.findById(id).orElseThrow().quotationNumber;
        String filename = String.format(Locale.ROOT, "CADPRINT-%s-R%02d.pdf", commercialNumber, number);
        try {
            return new Download(filename, storage.read(job.candidate()));
        } catch (IOException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El documento original no está disponible");
        }
    }

    private QuotationRevision issued(long id, int number) {
        var revision = revisions.findByQuotationIdAndRevisionNumber(id, number)
                .map(item -> Hibernate.unproxy(item, QuotationRevision.class))
                .orElseThrow(() -> QuotationService.missing("Revisión no encontrada"));
        if (revision.issuedAt == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La revisión todavía no se ha emitido");
        }
        return revision;
    }

    private Job job(long id) {
        return queue.find(id).orElseThrow(() -> QuotationService.missing("Documento no solicitado"));
    }

    private Metadata metadata(Job job) {
        var original = job.status() == Status.READY ? job.candidate() : null;
        return new Metadata(job.status(), job.templateVersion(), original == null ? null : original.sha256(),
                original == null ? null : original.size(), job.generatedAt(), job.error());
    }
}
