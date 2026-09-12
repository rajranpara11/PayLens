package com.paylens.seed;

import com.paylens.employee.repository.EmployeeRepository;
import com.paylens.seed.SeedModels.SeedDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Opt-in only via {@code paylens.seed.enabled=true} (e.g. profile {@code seed}).
 * Never enabled by default — production startups skip this bean entirely.
 */
@Component
@ConditionalOnProperty(prefix = "paylens.seed", name = "enabled", havingValue = "true")
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final SeedProperties properties;
    private final EmployeeRepository employeeRepository;
    private final JdbcBatchSeeder jdbcBatchSeeder;

    public SeedRunner(
            SeedProperties properties,
            EmployeeRepository employeeRepository,
            JdbcBatchSeeder jdbcBatchSeeder
    ) {
        this.properties = properties;
        this.employeeRepository = employeeRepository;
        this.jdbcBatchSeeder = jdbcBatchSeeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = employeeRepository.count();
        int target = properties.getEmployeeCount();
        if (existing >= target) {
            log.info(
                    "Seed skipped: database already has {} employees (target {}). Safe re-run.",
                    existing,
                    target
            );
            return;
        }
        if (existing > 0) {
            log.warn(
                    "Seed skipped: found {} existing employees (< {}). Truncate employee/salary/department tables to re-seed.",
                    existing,
                    target
            );
            return;
        }

        log.info(
                "Seeding {} employees with randomSeed={} batchSize={}",
                target,
                properties.getRandomSeed(),
                properties.getBatchSize()
        );
        SeedDataset dataset = DeterministicSeedGenerator.generate(properties.getRandomSeed(), target);
        jdbcBatchSeeder.seed(dataset, properties.getBatchSize());
        long after = employeeRepository.count();
        log.info("Seed complete. employee count={}", after);
        if (after != target) {
            throw new IllegalStateException("Expected " + target + " employees after seed, found " + after);
        }
    }
}
