package gt.cadprint.manager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import gt.cadprint.manager.QuotationDtos.*;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class QuotationService {
    private static final ZoneId COMMERCIAL_ZONE = ZoneId.of("America/Guatemala");
    private static final int DEFAULT_VALIDITY_DAYS = 15;

    private final QuotationRepo quotations;
    private final QuotationRevisionRepo revisions;
    private final QuotationLineRepo lines;
    private final QuotationEventRepo events;
    private final QuotationMoney money;
    private final QuotationLineFactory lineFactory;
    private final Issuer issuer;
    private final QuotationDocumentQueue documents;

    QuotationService(QuotationRepo quotations, QuotationRevisionRepo revisions, QuotationLineRepo lines,
                     QuotationEventRepo events, QuotationMoney money, QuotationLineFactory lineFactory,
                     QuotationDocumentQueue documents,
                     @Value("${cadprint.quotations.issuer-name:CADPRINT}") String issuerName,
                     @Value("${cadprint.quotations.issuer-email:}") String issuerEmail,
                     @Value("${cadprint.quotations.issuer-phone:}") String issuerPhone,
                     @Value("${cadprint.quotations.issuer-address:}") String issuerAddress) {
        this.quotations = quotations;
        this.revisions = revisions;
        this.lines = lines;
        this.events = events;
        this.money = money;
        this.lineFactory = lineFactory;
        this.documents = documents;
        this.issuer = new Issuer(clean(issuerName), clean(issuerEmail), clean(issuerPhone), clean(issuerAddress));
    }

    QuotationPage list(QuotationStatus status, String number, String recipient, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw invalid("Paginación inválida");
        }
        var results = revisions.search(status, filter(number), filter(recipient),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new QuotationPage(results.stream()
                .map(revision -> summary(Hibernate.unproxy(revision.quotation, Quotation.class), revision))
                .toList(), page, size, results.getTotalElements(), results.getTotalPages());
    }

    Detail detail(Long id) {
        Quotation quote = find(id);
        var latest = revisions.findAllByQuotationIdOrderByRevisionNumberDesc(id).getFirst();
        return detail(quote, Hibernate.unproxy(latest, QuotationRevision.class));
    }

    Detail detail(Long id, int number) {
        return detail(find(id), revision(id, number));
    }

    List<Summary> revisions(Long id) {
        Quotation quote = find(id);
        return revisions.findAllByQuotationIdOrderByRevisionNumberDesc(id).stream()
                .map(item -> summary(quote, Hibernate.unproxy(item, QuotationRevision.class))).toList();
    }

    List<EventView> events(Long id) {
        find(id);
        return events.findAllByRevisionQuotationIdOrderByOccurredAtAscIdAsc(id).stream()
                .map(event -> new EventView(revisionNumber(event.revision), event.eventType,
                        event.previousStatus, event.newStatus, event.actor, event.occurredAt, event.reason, event.details))
                .toList();
    }

    CommercialView commercial(Long id, int number) {
        return commercial(find(id), revision(id, number));
    }

    CommercialView commercialByRevisionId(Long id) {
        var revision = revisions.findById(id).orElseThrow(() -> missing("Revisión no encontrada"));
        return commercial(Hibernate.unproxy(revision.quotation, Quotation.class), revision);
    }

    private CommercialView commercial(Quotation quote, QuotationRevision revision) {
        if (revision.issuedAt == null) {
            throw conflict("La revisión todavía no se ha emitido");
        }
        return new CommercialView(quote.quotationNumber, revision.revisionNumber, revision.currency,
                recipient(revision), issuer(revision), revision.issuedAt, revision.validUntil, revision.commercialTerms,
                totals(revision), storedLines(revision).stream().map(this::commercialLine).toList());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    Detail create(DraftInput input, String actor) {
        var quote = new Quotation();
        quote.createdBy = actor;
        quotations.save(quote);
        var revision = newDraft(quote, 1);
        List<QuotationLine> calculated = applyDraft(revision, input);
        revisions.save(revision);
        calculated.forEach(lines::save);
        record(revision, QuotationEventType.CREATED, null, revision.status, actor, null, null);
        return savedDetail(quote, revision);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    Detail edit(Long id, int number, EditInput input, String actor) {
        Quotation quote = lock(id);
        QuotationRevision revision = revision(id, number);
        checkVersion(quote, input.version());
        requireDraft(revision);
        requireOpen(quote);
        List<QuotationLine> calculated = applyDraft(revision, input.draft());
        lines.deleteDraftLines(revision.id);
        calculated.forEach(lines::save);
        touch(quote, revision);
        record(revision, QuotationEventType.UPDATED, revision.status, revision.status, actor, null, null);
        return savedDetail(quote, revision);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    Detail revise(Long id, int sourceNumber, VersionInput input, String actor) {
        Quotation quote = lock(id);
        checkVersion(quote, input.version());
        List<QuotationRevision> history = history(id);
        requireOpen(history);
        if (history.stream().anyMatch(item -> item.status == QuotationStatus.BORRADOR)) {
            throw conflict("Ya existe un borrador en esta cotización");
        }
        QuotationRevision source = revision(id, sourceNumber);
        if (source.issuedAt == null) {
            throw conflict("Selecciona una revisión emitida como origen");
        }
        QuotationRevision draft = newDraft(quote, history.getFirst().revisionNumber + 1);
        draft.sourceRevision = source;
        copyHeader(source, draft);
        List<QuotationLine> copied = storedLines(source).stream().map(line -> lineFactory.copy(line, draft)).toList();
        money.calculate(draft, copied);
        revisions.save(draft);
        copied.forEach(lines::save);
        touch(quote, draft);
        record(draft, QuotationEventType.REVISED, null, draft.status, actor, null, "Origen: " + sourceNumber);
        return savedDetail(quote, draft);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    Detail emit(Long id, int number, VersionInput input, String actor) {
        Quotation quote = lock(id);
        QuotationRevision revision = revision(id, number);
        if (revision.issuedAt != null) {
            return detail(quote, revision);
        }
        checkVersion(quote, input.version());
        requireDraft(revision);
        List<QuotationRevision> history = history(id);
        requireOpen(history);

        List<QuotationLine> stored = storedLines(revision);
        if (revision.recipientName == null || stored.isEmpty()) {
            throw invalid("La emisión requiere destinatario y al menos una línea");
        }
        validateIssuer();
        Instant issuedAt = Instant.now();
        LocalDate today = issuedAt.atZone(COMMERCIAL_ZONE).toLocalDate();
        if (revision.validUntil == null) {
            revision.validUntil = today.plusDays(DEFAULT_VALIDITY_DAYS);
        } else if (revision.validUntil.isBefore(today)) {
            throw invalid("La vigencia no puede terminar antes de la emisión");
        }
        money.calculate(revision, stored);
        if (quote.quotationNumber == null) {
            // The database allocates a unique aggregate ID. Issuance is the only operation that exposes it as a number.
            quote.quotationNumber = String.format(Locale.ROOT, "COT-%06d", quote.id);
        }
        revision.issuerName = issuer.name();
        revision.issuerEmail = issuer.email();
        revision.issuerPhone = issuer.phone();
        revision.issuerAddress = issuer.address();
        revision.issuedAt = issuedAt;
        revision.status = QuotationStatus.EMITIDA;
        history.stream().filter(item -> !item.id.equals(revision.id) && item.issuedAt != null && item.replacedAt == null)
                .findFirst().ifPresent(previous -> {
                    previous.replacedByRevision = revision;
                    previous.replacedAt = issuedAt;
                    previous.updatedAt = issuedAt;
                    record(previous, QuotationEventType.REPLACED, previous.status, previous.status,
                            actor, null, "Sustituida por revisión " + revision.revisionNumber);
                });
        touch(quote, revision);
        record(revision, QuotationEventType.ISSUED, QuotationStatus.BORRADOR, revision.status, actor, null, null);
        documents.enqueue(revision.id, issuedAt);
        return savedDetail(quote, revision);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    Detail decide(Long id, int number, QuotationStatus target, String actor, String requestedReason) {
        Quotation quote = lock(id);
        QuotationRevision revision = revision(id, number);
        if (revision.status != QuotationStatus.EMITIDA || revision.replacedAt != null) {
            throw conflict("La revisión no es una emisión vigente");
        }
        boolean expired = revision.validUntil.isBefore(Instant.now().atZone(COMMERCIAL_ZONE).toLocalDate());
        if (expired != (target == QuotationStatus.VENCIDA)) {
            throw conflict(expired ? "La revisión está vencida" : "La revisión aún está vigente");
        }

        String reason = clean(requestedReason);
        QuotationEventType event = switch (target) {
            case ACEPTADA -> QuotationEventType.ACCEPTED;
            case RECHAZADA -> QuotationEventType.REJECTED;
            case VENCIDA -> QuotationEventType.EXPIRED;
            case CANCELADA -> {
                if (reason == null) {
                    throw invalid("El motivo de cancelación es obligatorio");
                }
                yield QuotationEventType.CANCELLED;
            }
            default -> throw conflict("Transición de estado inválida");
        };
        revision.status = target;
        touch(quote, revision);
        record(revision, event, QuotationStatus.EMITIDA, target, actor, reason, null);
        return savedDetail(quote, revision);
    }

    private List<QuotationLine> applyDraft(QuotationRevision revision, DraftInput input) {
        Recipient recipient = input.recipient();
        revision.recipientName = recipient == null ? null : clean(recipient.name());
        revision.recipientContact = recipient == null ? null : clean(recipient.contact());
        revision.recipientEmail = recipient == null ? null : clean(recipient.email());
        revision.recipientPhone = recipient == null ? null : clean(recipient.phone());
        revision.recipientAddress = recipient == null ? null : clean(recipient.address());
        revision.commercialTerms = clean(input.commercialTerms());
        revision.notes = clean(input.notes());
        revision.validUntil = input.validUntil();
        if (revision.validUntil != null && (revision.validUntil.getYear() < 1000 || revision.validUntil.getYear() > 9999)) {
            throw invalid("La fecha de vigencia está fuera del rango admitido");
        }
        revision.shippingAmount = input.shippingAmount() == null ? BigDecimal.ZERO : input.shippingAmount();
        Discount discount = money.normalize(input.generalDiscount());
        revision.generalDiscountType = discount.type();
        revision.generalDiscountValue = discount.type() == QuotationDiscountType.FIXED ? discount.value() : null;
        revision.generalDiscountPercent = discount.type() == QuotationDiscountType.PERCENTAGE ? discount.value() : null;
        List<QuotationLine> calculated = new ArrayList<>();
        for (LineInput line : input.lines()) {
            calculated.add(lineFactory.create(revision, calculated.size() + 1, line));
        }
        money.calculate(revision, calculated);
        return calculated;
    }

    private void copyHeader(QuotationRevision source, QuotationRevision draft) {
        draft.recipientName = source.recipientName;
        draft.recipientContact = source.recipientContact;
        draft.recipientEmail = source.recipientEmail;
        draft.recipientPhone = source.recipientPhone;
        draft.recipientAddress = source.recipientAddress;
        draft.commercialTerms = source.commercialTerms;
        draft.notes = source.notes;
        draft.generalDiscountType = source.generalDiscountType;
        draft.generalDiscountValue = source.generalDiscountValue;
        draft.generalDiscountPercent = source.generalDiscountPercent;
        draft.shippingAmount = source.shippingAmount;
    }

    private QuotationRevision newDraft(Quotation quote, int number) {
        var draft = new QuotationRevision();
        draft.quotation = quote;
        draft.revisionNumber = number;
        return draft;
    }

    private void touch(Quotation quote, QuotationRevision revision) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        quote.updatedAt = now.isAfter(quote.updatedAt) ? now : quote.updatedAt.plus(1, ChronoUnit.MICROS);
        revision.updatedAt = quote.updatedAt;
    }

    private void record(QuotationRevision revision, QuotationEventType type, QuotationStatus previous,
                        QuotationStatus current, String actor, String reason, String details) {
        var event = new QuotationEvent();
        event.revision = revision;
        event.eventType = type;
        event.previousStatus = previous;
        event.newStatus = current;
        event.actor = actor;
        event.reason = reason;
        event.details = details;
        events.save(event);
    }

    private Detail savedDetail(Quotation quote, QuotationRevision revision) {
        quotations.flush();
        return detail(quote, revision);
    }

    private Detail detail(Quotation quote, QuotationRevision revision) {
        return new Detail(summary(quote, revision), revisionNumber(revision.sourceRevision),
                revisionNumber(revision.replacedByRevision), revision.currency, recipient(revision), issuer(revision),
                revision.commercialTerms, revision.notes, revision.calculationPolicyVersion, totals(revision),
                storedLines(revision).stream().map(this::internalLine).toList());
    }

    private InternalLine internalLine(QuotationLine line) {
        var calculation = line.productionCalculation == null ? null
                : Hibernate.unproxy(line.productionCalculation, ProductionCalculation.class);
        // Compare without rounding or allocating the general discount; the historical lot may have differed.
        boolean belowCost = calculation != null && line.netAmount.compareTo(
                calculation.unitCost.multiply(BigDecimal.valueOf(line.quantity))) < 0;
        return new InternalLine(commercialLine(line), line.lineType, lineFactory.id(line.product),
                lineFactory.id(line.variant), lineFactory.id(calculation), belowCost);
    }

    private CommercialLine commercialLine(QuotationLine line) {
        return new CommercialLine(line.position, line.skuSnapshot, line.nameSnapshot, line.descriptionSnapshot,
                line.quantity, line.unitPrice, new Discount(line.discountType,
                QuotationMoney.discountValue(line.discountType, line.discountValue, line.discountPercent)),
                line.grossAmount, line.discountAmount, line.netAmount);
    }

    private Totals totals(QuotationRevision revision) {
        return new Totals(revision.grossAmount, revision.lineDiscountTotal, revision.subtotal,
                new Discount(revision.generalDiscountType, QuotationMoney.discountValue(revision.generalDiscountType,
                        revision.generalDiscountValue, revision.generalDiscountPercent)), revision.generalDiscountAmount,
                revision.shippingAmount, revision.totalAmount);
    }

    private Summary summary(Quotation quote, QuotationRevision revision) {
        return new Summary(quote.id, quote.quotationNumber, quote.version, revision.revisionNumber, revision.status,
                revision.recipientName, revision.issuedAt, revision.validUntil, revision.replacedAt, revision.totalAmount);
    }

    private Recipient recipient(QuotationRevision revision) {
        return new Recipient(revision.recipientName, revision.recipientContact, revision.recipientEmail,
                revision.recipientPhone, revision.recipientAddress);
    }

    private Issuer issuer(QuotationRevision revision) {
        return new Issuer(revision.issuerName, revision.issuerEmail, revision.issuerPhone, revision.issuerAddress);
    }

    private void validateIssuer() {
        if (issuer.name() == null || longerThan(issuer.name(), 255) || longerThan(issuer.email(), 254)
                || longerThan(issuer.phone(), 64) || longerThan(issuer.address(), 500)) {
            throw invalid("Configura datos válidos del emisor");
        }
    }

    private boolean longerThan(String value, int limit) {
        return value != null && value.length() > limit;
    }

    private List<QuotationLine> storedLines(QuotationRevision revision) {
        return lines.findAllByRevisionIdOrderByPositionAsc(revision.id);
    }

    private Integer revisionNumber(QuotationRevision revision) {
        return revision == null ? null : Hibernate.unproxy(revision, QuotationRevision.class).revisionNumber;
    }

    private List<QuotationRevision> history(Long id) {
        return revisions.findAllByQuotationIdOrderByRevisionNumberDesc(id).stream()
                .map(item -> Hibernate.unproxy(item, QuotationRevision.class)).toList();
    }

    private Quotation find(Long id) {
        return quotations.findById(id).orElseThrow(() -> missing("Cotización no encontrada"));
    }

    private Quotation lock(Long id) {
        return quotations.findByIdForUpdate(id).orElseThrow(() -> missing("Cotización no encontrada"));
    }

    private QuotationRevision revision(Long id, int number) {
        return revisions.findByQuotationIdAndRevisionNumber(id, number)
                .map(item -> Hibernate.unproxy(item, QuotationRevision.class))
                .orElseThrow(() -> missing("Revisión no encontrada"));
    }

    private void requireDraft(QuotationRevision revision) {
        if (revision.status != QuotationStatus.BORRADOR || revision.issuedAt != null) {
            throw conflict("Solo se puede editar o emitir un borrador");
        }
    }

    private void requireOpen(Quotation quote) {
        requireOpen(history(quote.id));
    }

    private void requireOpen(List<QuotationRevision> history) {
        if (history.stream().anyMatch(item -> item.status == QuotationStatus.ACEPTADA)) {
            throw conflict("La cotización aceptada cerró el ciclo de revisiones");
        }
    }

    private void checkVersion(Quotation quote, long version) {
        if (quote.version != version) {
            throw conflict("La versión de la cotización está desactualizada");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String filter(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static ResponseStatusException missing(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
