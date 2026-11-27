package gt.cadprint.manager;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "cadprint.quotation-documents.worker-enabled", havingValue = "true", matchIfMissing = true)
class QuotationDocumentScheduling {
    private final QuotationDocumentProcessor processor;

    QuotationDocumentScheduling(QuotationDocumentProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${cadprint.quotation-documents.poll-ms:5000}",
               initialDelayString = "${cadprint.quotation-documents.poll-ms:5000}")
    void processDocuments() {
        processor.processDue();
    }
}
