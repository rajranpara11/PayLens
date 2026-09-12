package com.paylens.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.paylens.seed.SeedModels.EmployeeSeed;
import com.paylens.seed.SeedModels.SeedDataset;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DeterministicSeedGeneratorTest {

    @Test
    void generatesExactlyRequestedCount() {
        SeedDataset dataset = DeterministicSeedGenerator.generate(42L, 10_000);
        assertThat(dataset.employees()).hasSize(10_000);
        assertThat(dataset.departments()).hasSize(10);
    }

    @Test
    void isDeterministic() {
        SeedDataset first = DeterministicSeedGenerator.generate(42L, 500);
        SeedDataset second = DeterministicSeedGenerator.generate(42L, 500);

        assertThat(first.employees()).isEqualTo(second.employees());
        assertThat(first.departments()).isEqualTo(second.departments());
    }

    @Test
    void employeeCodesAndEmailsAreUnique() {
        SeedDataset dataset = DeterministicSeedGenerator.generate(42L, 10_000);
        Set<String> codes = new HashSet<>();
        Set<String> emails = new HashSet<>();
        for (EmployeeSeed employee : dataset.employees()) {
            assertThat(codes.add(employee.employeeCode())).isTrue();
            assertThat(emails.add(employee.email())).isTrue();
        }
        assertThat(codes).hasSize(10_000);
        assertThat(emails).hasSize(10_000);
    }

    @Test
    void countryCurrencyAndSalaryAreConsistent() {
        Map<String, String> expected = Map.of(
                "IN", "INR",
                "US", "USD",
                "GB", "GBP",
                "DE", "EUR",
                "CA", "CAD"
        );
        SeedDataset dataset = DeterministicSeedGenerator.generate(42L, 10_000);
        for (EmployeeSeed employee : dataset.employees()) {
            assertThat(expected).containsKey(employee.country());
            assertThat(employee.salaries()).isNotEmpty();
            for (var salary : employee.salaries()) {
                assertThat(salary.currency()).isEqualTo(expected.get(employee.country()));
                assertThat(salary.annualSalary()).isGreaterThan(BigDecimal.ZERO);
                assertThat(salary.annualSalary().scale()).isLessThanOrEqualTo(2);
            }
        }
    }

    @Test
    void seniorEngineeringTendsToEarnMoreThanJuniorInSameCountry() {
        SeedDataset dataset = DeterministicSeedGenerator.generate(42L, 10_000);
        var usEngineers = dataset.employees().stream()
                .filter(e -> "US".equals(e.country()))
                .filter(e -> e.designation().contains("Software Engineer"))
                .toList();

        double juniorAvg = usEngineers.stream()
                .filter(e -> e.designation().startsWith("Junior"))
                .map(e -> e.salaries().getFirst().annualSalary())
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);
        double seniorAvg = usEngineers.stream()
                .filter(e -> e.designation().startsWith("Senior"))
                .map(e -> e.salaries().getFirst().annualSalary())
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);

        assertThat(seniorAvg).isGreaterThan(juniorAvg);
    }

    @Test
    void coversSuggestedDepartmentsAndStatuses() {
        SeedDataset dataset = DeterministicSeedGenerator.generate(42L, 10_000);
        assertThat(dataset.departments())
                .extracting(d -> d.name())
                .contains(
                        "Engineering",
                        "Product",
                        "Human Resources",
                        "Finance",
                        "Sales",
                        "Marketing",
                        "Operations",
                        "Legal",
                        "IT",
                        "Customer Success"
                );
        Set<String> statuses = dataset.employees().stream()
                .map(EmployeeSeed::employmentStatus)
                .collect(Collectors.toSet());
        assertThat(statuses).contains("ACTIVE", "ON_LEAVE", "TERMINATED");
    }

    @Test
    void someEmployeesHaveSalaryHistory() {
        SeedDataset dataset = DeterministicSeedGenerator.generate(42L, 10_000);
        long withHistory = dataset.employees().stream().filter(e -> e.salaries().size() > 1).count();
        assertThat(withHistory).isGreaterThan(1000);
    }
}
