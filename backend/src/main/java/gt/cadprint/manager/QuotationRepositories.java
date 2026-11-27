package gt.cadprint.manager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface QuotationRepo extends Repository<Quotation, Long> {
    Quotation save(Quotation quotation);

    void flush();

    Optional<Quotation> findById(Long id);

    Optional<Quotation> findByQuotationNumber(String quotationNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quotation q where q.id = :id")
    Optional<Quotation> findByIdForUpdate(@Param("id") Long id);
}

interface QuotationRevisionRepo extends Repository<QuotationRevision, Long> {
    Optional<QuotationRevision> findById(Long id);

    QuotationRevision save(QuotationRevision revision);

    List<QuotationRevision> findAllByQuotationIdOrderByRevisionNumberDesc(Long quotationId);

    Optional<QuotationRevision> findByQuotationIdAndRevisionNumber(Long quotationId, int revisionNumber);

    @EntityGraph(attributePaths = "quotation")
    @Query("""
            select r from QuotationRevision r join r.quotation q
            where r.revisionNumber = (select max(other.revisionNumber) from QuotationRevision other where other.quotation = q)
            and (:status is null or r.status = :status)
            and (:number = '' or locate(:number, lower(q.quotationNumber)) > 0)
            and (:recipient = '' or locate(:recipient, lower(r.recipientName)) > 0)
            """)
    Page<QuotationRevision> search(@Param("status") QuotationStatus status, @Param("number") String number,
                                   @Param("recipient") String recipient, Pageable pageable);

    List<QuotationRevision> findAllByStatusAndValidUntilBeforeOrderByValidUntilAscIdAsc(
            QuotationStatus status, LocalDate cutoff);
}

interface QuotationLineRepo extends Repository<QuotationLine, Long> {
    QuotationLine save(QuotationLine line);

    @Modifying
    @Query("delete from QuotationLine line where line.revision.id = :revisionId")
    void deleteDraftLines(@Param("revisionId") Long revisionId);

    List<QuotationLine> findAllByRevisionIdOrderByPositionAsc(Long revisionId);
}

interface QuotationEventRepo extends Repository<QuotationEvent, Long> {
    QuotationEvent save(QuotationEvent event);

    List<QuotationEvent> findAllByRevisionQuotationIdOrderByOccurredAtAscIdAsc(Long quotationId);

    List<QuotationEvent> findAllByRevisionIdOrderByOccurredAtAscIdAsc(Long revisionId);
}

