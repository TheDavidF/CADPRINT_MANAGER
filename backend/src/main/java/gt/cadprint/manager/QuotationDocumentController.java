package gt.cadprint.manager;

import gt.cadprint.manager.QuotationDocumentService.Metadata;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotations/{id}/revisions/{number}/document")
class QuotationDocumentController {
    private final QuotationDocumentService documents;

    QuotationDocumentController(QuotationDocumentService documents) {
        this.documents = documents;
    }

    @PostMapping
    ResponseEntity<Metadata> request(@PathVariable long id, @PathVariable int number) {
        var result = documents.request(id, number);
        return ResponseEntity.status(result.status() == QuotationDocumentQueue.Status.READY
                ? HttpStatus.OK : HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping
    Metadata status(@PathVariable long id, @PathVariable int number) {
        return documents.status(id, number);
    }

    @GetMapping("/download")
    ResponseEntity<byte[]> download(@PathVariable long id, @PathVariable int number) {
        var original = documents.download(id, number);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(original.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(original.bytes());
    }
}
