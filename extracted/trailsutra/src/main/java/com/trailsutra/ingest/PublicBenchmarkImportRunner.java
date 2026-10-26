package com.trailsutra.ingest;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class PublicBenchmarkImportRunner implements CommandLineRunner {
    private final PublicBenchmarkCsvImporter importer;

    public PublicBenchmarkImportRunner(PublicBenchmarkCsvImporter importer) {
        this.importer = importer;
    }

    @Override
    public void run(String... args) {
        importer.importFile(Path.of("data/import/ibm_aml.csv"));
    }
}
