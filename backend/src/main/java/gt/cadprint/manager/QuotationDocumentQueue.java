package gt.cadprint.manager;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class QuotationDocumentQueue {
    enum Status { PENDING, PROCESSING, READY, FAILED }

    record Candidate(String key, String sha256, long size) {}
    record Job(long revisionId, Status status, String templateVersion, Candidate candidate,
               Instant generatedAt, int attempts, String error) {}

    private static final String DUE = """
            ((status = 'PENDING' AND next_attempt_at <= ?) OR (status = 'PROCESSING' AND lease_until <= ?))
            """;
    private static final RowMapper<Job> JOB = (rs, row) -> {
        String key = rs.getString("storage_key");
        Candidate candidate = key == null ? null : new Candidate(key, rs.getString("sha256"), rs.getLong("size_bytes"));
        var generated = rs.getObject("generated_at", OffsetDateTime.class);
        return new Job(rs.getLong("revision_id"), Status.valueOf(rs.getString("status")),
                rs.getString("template_version"), candidate, generated == null ? null : generated.toInstant(),
                rs.getInt("attempt_count"), rs.getString("last_error_code"));
    };

    private final JdbcTemplate jdbc;

    QuotationDocumentQueue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void enqueue(long id, Instant now) {
        jdbc.update("""
                INSERT INTO quotation_documents (revision_id, status, template_version, created_at, next_attempt_at)
                VALUES (?, 'PENDING', ?, ?, ?)
                """, id, QuotationPdfRenderer.VERSION, utc(now), utc(now));
    }

    Optional<Job> find(long id) {
        return jdbc.query("SELECT * FROM quotation_documents WHERE revision_id = ?", JOB, id).stream().findFirst();
    }

    List<Long> due(Instant now) {
        return jdbc.queryForList("SELECT revision_id FROM quotation_documents WHERE " + DUE +
                " ORDER BY revision_id LIMIT 16", Long.class, utc(now), utc(now));
    }

    boolean claim(long id, String token, Instant now, Instant until) {
        return jdbc.update("""
                UPDATE quotation_documents SET status = 'PROCESSING', lease_token = ?, lease_until = ?,
                    attempt_count = attempt_count + 1, next_attempt_at = NULL
                WHERE revision_id = ? AND
                """ + DUE, token, utc(until), id, utc(now), utc(now)) == 1;
    }

    boolean stage(long id, String token, Candidate candidate) {
        return jdbc.update("""
                UPDATE quotation_documents SET storage_key = ?, sha256 = ?, size_bytes = ?
                WHERE revision_id = ? AND status = 'PROCESSING' AND lease_token = ?
                """, candidate.key(), candidate.sha256(), candidate.size(), id, token) == 1;
    }

    void ready(long id, String token, Instant now) {
        jdbc.update("""
                UPDATE quotation_documents SET status = 'READY', generated_at = ?, lease_token = NULL,
                    lease_until = NULL, last_error_code = NULL
                WHERE revision_id = ? AND status = 'PROCESSING' AND lease_token = ?
                """, utc(now), id, token);
    }

    void fail(long id, String token, Instant retry, String code) {
        jdbc.update("""
                UPDATE quotation_documents SET status = ?, next_attempt_at = ?, last_error_code = ?,
                    lease_token = NULL, lease_until = NULL
                WHERE revision_id = ? AND status = 'PROCESSING' AND lease_token = ?
                """, retry == null ? "FAILED" : "PENDING", utc(retry), code, id, token);
    }

    void retry(long id, Instant now) {
        jdbc.update("""
                UPDATE quotation_documents SET status = 'PENDING', next_attempt_at = ?, attempt_count = 0,
                    last_error_code = NULL WHERE revision_id = ? AND status = 'FAILED'
                """, utc(now), id);
    }

    private static OffsetDateTime utc(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
