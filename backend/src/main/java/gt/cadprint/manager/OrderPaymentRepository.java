package gt.cadprint.manager;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OrderPaymentRepository extends JpaRepository<OrderPayment, Long> {
    List<OrderPayment> findAllByCustomerOrderIdOrderByIdAsc(Long orderId);
    @Query("""
            select new gt.cadprint.manager.DailyCashPayment(p.id, o.id, q.quotationNumber,
                       p.amount, p.method, p.receivedAt, p.receivedBy)
            from OrderPayment p
            join CustomerOrder o on o.id = p.customerOrderId
            join Quotation q on q.id = o.quotationId
            where p.receivedAt >= :start and p.receivedAt < :end
            order by p.receivedAt, p.id
            """)
    List<DailyCashPayment> findDailyPayments(Instant start, Instant end);
}
