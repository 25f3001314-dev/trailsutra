package com.trailsutra.ingest;

import com.trailsutra.tokenisation.TokenisationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicBenchmarkCsvImporterTest {
    @TempDir
    Path tempDir;

    @Test
    void importsInrTransactionsWithTokensProvenanceAndDeduplication() throws Exception {
        String csv = """
                Timestamp,From Bank,Account,To Bank,Account,Amount Received,Receiving Currency,Amount Paid,Payment Currency,Payment Format,Is Laundering
                100,bank-a,account-1,bank-b,account-2,125.50,INR,125.50,INR,UPI,0
                200,bank-a,account-3,bank-b,account-4,2.00,USD,2.00,INR,Wire,1
                """;
        Path file = tempDir.resolve("ibm_aml.csv");
        Files.writeString(file, csv);
        JdbcTemplate jdbc = database();
        TokenisationService tokens = new TokenisationService("test-hmac-key", "test-encryption-key");
        PublicBenchmarkCsvImporter importer = new PublicBenchmarkCsvImporter(
                jdbc, new SourceIntegrityService(jdbc), tokens);

        PublicBenchmarkCsvImporter.ImportResult first = importer.importFile(file);
        PublicBenchmarkCsvImporter.ImportResult second = importer.importFile(file);

        assertThat(first.rows()).isEqualTo(2);
        assertThat(first.inserted()).isEqualTo(1);
        assertThat(first.skippedCurrencies()).isEqualTo(1);
        assertThat(second.inserted()).isZero();
        assertThat(second.duplicates()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_paise FROM transactions", Long.class)).isEqualTo(12550L);
        assertThat(jdbc.queryForObject("SELECT src_token FROM transactions", String.class))
                .isEqualTo(tokens.accountToken("bank-a", "account-1"));
        assertThat(jdbc.queryForObject("SELECT dst_token FROM transactions", String.class))
                .isEqualTo(tokens.accountToken("bank-b", "account-2"));
        assertThat(jdbc.queryForObject("SELECT purpose FROM source_imports LIMIT 1", String.class))
                .isEqualTo(PublicBenchmarkCsvImporter.PURPOSE);
        assertThat(jdbc.queryForObject("SELECT authority_reference FROM source_imports LIMIT 1", String.class))
                .isEqualTo(PublicBenchmarkCsvImporter.AUTHORITY_REFERENCE);
        assertThat(first.sourceHash()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
    }

    @Test
    void missingFileReturnsClearResultWithoutWritingProvenance() {
        JdbcTemplate jdbc = database();
        PublicBenchmarkCsvImporter importer = new PublicBenchmarkCsvImporter(jdbc,
                new SourceIntegrityService(jdbc),
                new TokenisationService("test-hmac-key", "test-encryption-key"));

        PublicBenchmarkCsvImporter.ImportResult result = importer.importFile(tempDir.resolve("missing.csv"));

        assertThat(result.inserted()).isZero();
        assertThat(result.message()).contains("file not found");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_imports", Integer.class)).isZero();
    }

    private JdbcTemplate database() {
        String name = "benchmark_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(dataSource);
    }
}
