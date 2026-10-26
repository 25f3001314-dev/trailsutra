package com.trailsutra.ingest;

import com.trailsutra.tokenisation.TokenisationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

@Service
public class PublicBenchmarkCsvImporter {
    public static final String PURPOSE = "Public benchmark data";
    public static final String AUTHORITY_REFERENCE = "IBM AML public benchmark";
    private static final Logger LOGGER = Logger.getLogger(PublicBenchmarkCsvImporter.class.getName());
    private static final DateTimeFormatter[] LOCAL_TIMESTAMPS = {
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    };

    private final JdbcTemplate jdbc;
    private final SourceIntegrityService integrity;
    private final TokenisationService tokens;

    public PublicBenchmarkCsvImporter(JdbcTemplate jdbc, SourceIntegrityService integrity,
                                      TokenisationService tokens) {
        this.jdbc = jdbc;
        this.integrity = integrity;
        this.tokens = tokens;
    }

    @Transactional
    public ImportResult importFile(Path file) {
        if (!Files.isRegularFile(file)) {
            String message = "Public benchmark data file not found: " + file;
            LOGGER.info(message);
            return new ImportResult("", 0, 0, 0, 0, message);
        }

        Validation validation = validateFile(file);
        String sourceHash = integrity.record(file, PURPOSE, AUTHORITY_REFERENCE, "system");
        int inserted = 0;
        int duplicates = 0;
        int skippedCurrencies = 0;
        Set<String> seen = new HashSet<>();

        try (PushbackReader reader = open(file)) {
            Header header = readHeader(reader);
            List<String> record;
            long rowNumber = 0;
            while ((record = readRecord(reader)) != null) {
                if (record.size() == 1 && record.get(0).isBlank()) {
                    continue;
                }
                rowNumber++;
                if (record.size() != header.columns().size()) {
                    throw rowError(rowNumber, "Column count does not match the header");
                }
                String paymentCurrency = value(record, header.paymentCurrency());
                String receivingCurrency = value(record, header.receivingCurrency());
                if (!isInr(paymentCurrency) || !isInr(receivingCurrency)) {
                    skippedCurrencies++;
                    continue;
                }
                TransactionRow transaction = transaction(record, header, sourceHash, rowNumber);
                if (!seen.add(transaction.txnId())) {
                    duplicates++;
                    continue;
                }
                int changed = jdbc.update("""
                        INSERT INTO transactions(txn_id,reference_token,src_token,dst_token,
                            initiated_at,posted_at,amount_paise,rail,status,source_hash)
                        SELECT ?,?,?,?,?,?,?,?,'POSTED',?
                        WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE txn_id=?)
                        """,
                        transaction.txnId(), transaction.reference(), transaction.sourceToken(),
                        transaction.destinationToken(), transaction.timestamp(), transaction.timestamp(),
                        transaction.amountPaise(), transaction.rail(), sourceHash, transaction.txnId());
                if (changed == 0) {
                    duplicates++;
                } else {
                    inserted++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Public benchmark data import failed for " + file, e);
        }

        if (skippedCurrencies != validation.skippedCurrencies()) {
            throw new IllegalStateException("Public benchmark data changed during import: " + file);
        }
        String message = "Public benchmark data import complete: " + inserted + " inserted, "
                + duplicates + " already present, " + skippedCurrencies
                + " rows skipped because both currencies must be INR; source SHA-256 " + sourceHash;
        LOGGER.info(message);
        return new ImportResult(sourceHash, validation.rowCount(), inserted, duplicates,
                skippedCurrencies, message);
    }

    private Validation validateFile(Path file) {
        int rows = 0;
        int skippedCurrencies = 0;
        try (PushbackReader reader = open(file)) {
            Header header = readHeader(reader);
            List<String> record;
            while ((record = readRecord(reader)) != null) {
                if (record.size() == 1 && record.get(0).isBlank()) {
                    continue;
                }
                rows++;
                if (record.size() != header.columns().size()) {
                    throw rowError(rows, "Column count does not match the header");
                }
                if (!isInr(value(record, header.paymentCurrency()))
                        || !isInr(value(record, header.receivingCurrency()))) {
                    skippedCurrencies++;
                    continue;
                }
                transaction(record, header, "validation", rows);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read public benchmark data file: " + file, e);
        }
        return new Validation(rows, skippedCurrencies);
    }

    private Header readHeader(PushbackReader reader) throws IOException {
        List<String> columns = readRecord(reader);
        if (columns == null) {
            throw new IllegalArgumentException("Public benchmark data file is empty");
        }
        return new Header(columns,
                requiredIndex(columns, "Timestamp"),
                requiredIndex(columns, "From Bank"),
                requiredIndex(columns, "Account", 0),
                requiredIndex(columns, "To Bank"),
                requiredIndex(columns, "Account", 1),
                requiredIndex(columns, "Amount Received"),
                requiredIndex(columns, "Receiving Currency"),
                requiredIndex(columns, "Amount Paid"),
                requiredIndex(columns, "Payment Currency"),
                requiredIndex(columns, "Payment Format"),
                optionalIndex(columns, "Transaction ID"),
                optionalIndex(columns, "Reference"));
    }

    private int requiredIndex(List<String> columns, String name) {
        return requiredIndex(columns, name, 0);
    }

    private int requiredIndex(List<String> columns, String name, int occurrence) {
        int found = 0;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).trim().equalsIgnoreCase(name) && found++ == occurrence) {
                return i;
            }
        }
        throw new IllegalArgumentException("Required CSV column is missing: " + name
                + (name.equals("Account") ? " (" + (occurrence == 0 ? "source" : "destination") + ")" : ""));
    }

    private int optionalIndex(List<String> columns, String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private TransactionRow transaction(List<String> record, Header header, String sourceHash,
                                       long rowNumber) {
        String fromBank = value(record, header.fromBank());
        String fromAccount = value(record, header.fromAccount());
        String toBank = value(record, header.toBank());
        String toAccount = value(record, header.toAccount());
        String paymentFormat = value(record, header.paymentFormat()).trim();
        if (fromBank.isBlank() || fromAccount.isBlank() || toBank.isBlank() || toAccount.isBlank()) {
            throw rowError(rowNumber, "Bank and account columns must not be empty");
        }
        if (paymentFormat.isBlank() || paymentFormat.length() > 20) {
            throw rowError(rowNumber, "Payment Format must contain between 1 and 20 characters");
        }

        long amountPaise;
        try {
            amountPaise = new BigDecimal(value(record, header.amountPaid()).replace(",", "").trim())
                    .movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw rowError(rowNumber, "Amount Paid must be a positive INR amount with at most two decimals");
        }
        if (amountPaise <= 0) {
            throw rowError(rowNumber, "Amount Paid must be positive");
        }

        Instant timestamp = parseTimestamp(value(record, header.timestamp()), rowNumber);
        String txnId;
        if (header.transactionId() >= 0 && !value(record, header.transactionId()).isBlank()) {
            txnId = "IBM-" + value(record, header.transactionId()).trim();
        } else {
            txnId = "IBM-" + sourceHash.substring(0, Math.min(40, sourceHash.length()))
                    + "-" + rowNumber;
        }
        if (txnId.length() > 100) {
            throw rowError(rowNumber, "Transaction ID exceeds the database column limit");
        }

        String reference = header.reference() < 0 || value(record, header.reference()).isBlank()
                ? null : value(record, header.reference()).trim();
        if (reference != null && reference.length() > 128) {
            throw rowError(rowNumber, "Reference exceeds the database column limit");
        }
        return new TransactionRow(txnId, reference, tokens.accountToken(fromBank, fromAccount),
                tokens.accountToken(toBank, toAccount), timestamp, amountPaise, paymentFormat);
    }

    private Instant parseTimestamp(String value, long rowNumber) {
        String timestamp = value.trim();
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(timestamp).toInstant();
            } catch (DateTimeParseException ignoredOffset) {
                for (DateTimeFormatter formatter : LOCAL_TIMESTAMPS) {
                    try {
                        return LocalDateTime.parse(timestamp, formatter).toInstant(ZoneOffset.UTC);
                    } catch (DateTimeParseException ignoredLocal) {
                        // Try the next supported source timestamp representation.
                    }
                }
                try {
                    return Instant.ofEpochSecond(Long.parseLong(timestamp));
                } catch (NumberFormatException | ArithmeticException ignoredNumeric) {
                    // Numeric source timestamps are elapsed seconds from the Unix epoch.
                }
            }
        }
        throw rowError(rowNumber, "Timestamp is not a supported date and time");
    }

    private String value(List<String> record, int index) {
        return record.get(index);
    }

    private boolean isInr(String currency) {
        return "INR".equalsIgnoreCase(currency.trim());
    }

    private IllegalArgumentException rowError(long row, String message) {
        return new IllegalArgumentException("Public benchmark CSV record " + row + ": " + message);
    }

    private PushbackReader open(Path file) throws IOException {
        return new PushbackReader(
                new BufferedReader(Files.newBufferedReader(file, StandardCharsets.UTF_8)), 1);
    }

    private List<String> readRecord(PushbackReader reader) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean sawInput = false;
        int next;
        while ((next = reader.read()) != -1) {
            sawInput = true;
            char current = (char) next;
            if (current == '"') {
                if (quoted) {
                    int following = reader.read();
                    if (following == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        if (following != -1) {
                            reader.unread(following);
                        }
                    }
                } else if (field.length() == 0) {
                    quoted = true;
                } else {
                    throw new IOException("Unexpected quote in CSV field");
                }
            } else if (current == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r') {
                    int following = reader.read();
                    if (following != '\n' && following != -1) {
                        reader.unread(following);
                    }
                }
                fields.add(field.toString());
                return fields;
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new IOException("Unclosed quoted CSV field");
        }
        if (!sawInput && fields.isEmpty() && field.isEmpty()) {
            return null;
        }
        fields.add(field.toString());
        return fields;
    }

    public record ImportResult(String sourceHash, int rows, int inserted, int duplicates,
                               int skippedCurrencies, String message) {}

    private record Header(List<String> columns, int timestamp, int fromBank, int fromAccount,
                          int toBank, int toAccount, int amountReceived, int receivingCurrency,
                          int amountPaid, int paymentCurrency, int paymentFormat, int transactionId,
                          int reference) {}

    private record TransactionRow(String txnId, String reference, String sourceToken,
                                  String destinationToken, Instant timestamp, long amountPaise,
                                  String rail) {}

    private record Validation(int rowCount, int skippedCurrencies) {}
}
