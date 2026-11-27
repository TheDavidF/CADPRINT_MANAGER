package gt.cadprint.manager;

import java.net.URI;
import java.util.List;

import gt.cadprint.manager.QuotationDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotations")
class QuotationController {
    private final QuotationService quotations;

    QuotationController(QuotationService quotations) {
        this.quotations = quotations;
    }

    @PostMapping
    ResponseEntity<Detail> create(@Valid @RequestBody DraftInput input, Authentication actor) {
        Detail created = quotations.create(input, actor.getName());
        return ResponseEntity.created(revisionUri(created)).body(created);
    }

    @GetMapping
    QuotationPage list(@RequestParam(required = false) QuotationStatus status,
                       @RequestParam(required = false) String number,
                       @RequestParam(required = false) String recipient,
                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return quotations.list(status, number, recipient, page, size);
    }

    @GetMapping("/{id}")
    Detail detail(@PathVariable Long id) {
        return quotations.detail(id);
    }

    @GetMapping("/{id}/revisions/{number}")
    Detail revision(@PathVariable Long id, @PathVariable int number) {
        return quotations.detail(id, number);
    }

    @GetMapping("/{id}/revisions")
    List<Summary> revisions(@PathVariable Long id) {
        return quotations.revisions(id);
    }

    @GetMapping("/{id}/events")
    List<EventView> events(@PathVariable Long id) {
        return quotations.events(id);
    }

    @GetMapping("/{id}/revisions/{number}/commercial")
    CommercialView commercial(@PathVariable Long id, @PathVariable int number) {
        return quotations.commercial(id, number);
    }

    @PutMapping("/{id}/revisions/{number}/draft")
    Detail edit(@PathVariable Long id, @PathVariable int number,
                @Valid @RequestBody EditInput input, Authentication actor) {
        return quotations.edit(id, number, input, actor.getName());
    }

    @PostMapping("/{id}/revisions/{number}/revise")
    ResponseEntity<Detail> revise(@PathVariable Long id, @PathVariable int number,
                                  @Valid @RequestBody VersionInput input, Authentication actor) {
        Detail created = quotations.revise(id, number, input, actor.getName());
        return ResponseEntity.created(revisionUri(created)).body(created);
    }

    @PostMapping("/{id}/revisions/{number}/emit")
    Detail emit(@PathVariable Long id, @PathVariable int number,
                @Valid @RequestBody VersionInput input, Authentication actor) {
        return quotations.emit(id, number, input, actor.getName());
    }

    @PostMapping("/{id}/revisions/{number}/accept")
    Detail accept(@PathVariable Long id, @PathVariable int number,
                  @Valid @RequestBody(required = false) DecisionInput input, Authentication actor) {
        return quotations.decide(id, number, QuotationStatus.ACEPTADA, actor.getName(), reason(input));
    }

    @PostMapping("/{id}/revisions/{number}/reject")
    Detail reject(@PathVariable Long id, @PathVariable int number,
                  @Valid @RequestBody(required = false) DecisionInput input, Authentication actor) {
        return quotations.decide(id, number, QuotationStatus.RECHAZADA, actor.getName(), reason(input));
    }

    @PostMapping("/{id}/revisions/{number}/expire")
    Detail expire(@PathVariable Long id, @PathVariable int number, Authentication actor) {
        return quotations.decide(id, number, QuotationStatus.VENCIDA, actor.getName(), null);
    }

    @PostMapping("/{id}/revisions/{number}/cancel")
    Detail cancel(@PathVariable Long id, @PathVariable int number,
                  @Valid @RequestBody DecisionInput input, Authentication actor) {
        return quotations.decide(id, number, QuotationStatus.CANCELADA, actor.getName(), reason(input));
    }

    private String reason(DecisionInput input) {
        return input == null ? null : input.reason();
    }

    private URI revisionUri(Detail detail) {
        return URI.create("/api/quotations/" + detail.revision().quotationId()
                + "/revisions/" + detail.revision().revisionNumber());
    }
}
