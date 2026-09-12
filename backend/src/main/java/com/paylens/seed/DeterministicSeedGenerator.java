package com.paylens.seed;

import com.paylens.seed.SeedModels.DepartmentSeed;
import com.paylens.seed.SeedModels.EmployeeSeed;
import com.paylens.seed.SeedModels.SalarySeed;
import com.paylens.seed.SeedModels.SeedDataset;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Pure deterministic generator. Same {@code randomSeed} + {@code employeeCount} ⇒ same dataset.
 */
public final class DeterministicSeedGenerator {

    private static final String[] FIRST_NAMES = {
            "Aarav", "Aditi", "Aisha", "Alex", "Amelia", "Anika", "Arjun", "Ava", "Benjamin", "Chloe",
            "Daniel", "Diya", "Elena", "Emma", "Ethan", "Grace", "Hannah", "Harper", "Ishaan", "James",
            "Kabir", "Liam", "Lucas", "Maya", "Mia", "Noah", "Olivia", "Oscar", "Priya", "Rahul",
            "Riya", "Samuel", "Sofia", "Sneha", "Thomas", "Victoria", "William", "Zara", "Mateo", "Nora"
    };

    private static final String[] LAST_NAMES = {
            "Anderson", "Patel", "Sharma", "Singh", "Brown", "Garcia", "Miller", "Davis", "Wilson", "Moore",
            "Taylor", "Thomas", "Jackson", "White", "Harris", "Martin", "Thompson", "Garcia", "Martinez", "Robinson",
            "Clark", "Rodriguez", "Lewis", "Lee", "Walker", "Hall", "Allen", "Young", "King", "Wright",
            "Scott", "Green", "Baker", "Adams", "Nelson", "Hill", "Ramirez", "Campbell", "Mitchell", "Roberts"
    };

    private static final CountryProfile[] COUNTRIES = {
            new CountryProfile("IN", "INR", new BigDecimal("600000"), new BigDecimal("4500000")),
            new CountryProfile("US", "USD", new BigDecimal("55000"), new BigDecimal("220000")),
            new CountryProfile("GB", "GBP", new BigDecimal("35000"), new BigDecimal("140000")),
            new CountryProfile("DE", "EUR", new BigDecimal("40000"), new BigDecimal("150000")),
            new CountryProfile("CA", "CAD", new BigDecimal("50000"), new BigDecimal("180000"))
    };

    private static final DeptDef[] DEPARTMENTS = {
            new DeptDef("ENG", "Engineering", new String[]{
                    "Junior Software Engineer", "Software Engineer", "Senior Software Engineer",
                    "Staff Engineer", "Engineering Manager"
            }),
            new DeptDef("PRD", "Product", new String[]{
                    "Associate Product Manager", "Product Manager", "Senior Product Manager", "Director of Product"
            }),
            new DeptDef("HR", "Human Resources", new String[]{
                    "HR Coordinator", "HR Business Partner", "Senior HR Business Partner", "HR Director"
            }),
            new DeptDef("FIN", "Finance", new String[]{
                    "Junior Accountant", "Financial Analyst", "Senior Financial Analyst", "Finance Manager"
            }),
            new DeptDef("SAL", "Sales", new String[]{
                    "Sales Development Representative", "Account Executive", "Senior Account Executive", "Sales Manager"
            }),
            new DeptDef("MKT", "Marketing", new String[]{
                    "Marketing Coordinator", "Marketing Specialist", "Senior Marketing Manager", "Marketing Director"
            }),
            new DeptDef("OPS", "Operations", new String[]{
                    "Operations Coordinator", "Operations Analyst", "Operations Manager", "Director of Operations"
            }),
            new DeptDef("LEG", "Legal", new String[]{
                    "Legal Assistant", "Corporate Counsel", "Senior Counsel", "General Counsel"
            }),
            new DeptDef("IT", "IT", new String[]{
                    "IT Support Specialist", "Systems Administrator", "Senior Systems Engineer", "IT Manager"
            }),
            new DeptDef("CS", "Customer Success", new String[]{
                    "Customer Success Associate", "Customer Success Manager",
                    "Senior Customer Success Manager", "Head of Customer Success"
            })
    };

    private static final String[] STATUSES = {"ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE", "ON_LEAVE", "TERMINATED"};

    private DeterministicSeedGenerator() {
    }

    public static SeedDataset generate(long randomSeed, int employeeCount) {
        if (employeeCount < 1) {
            throw new IllegalArgumentException("employeeCount must be >= 1");
        }
        Random random = new Random(randomSeed);
        List<DepartmentSeed> departments = new ArrayList<>(DEPARTMENTS.length);
        for (int i = 0; i < DEPARTMENTS.length; i++) {
            DeptDef def = DEPARTMENTS[i];
            departments.add(new DepartmentSeed(deterministicUuid(randomSeed, "dept", i), def.code(), def.name()));
        }

        List<EmployeeSeed> employees = new ArrayList<>(employeeCount);
        for (int i = 1; i <= employeeCount; i++) {
            employees.add(buildEmployee(random, randomSeed, i, departments));
        }
        return new SeedDataset(List.copyOf(departments), List.copyOf(employees));
    }

    private static EmployeeSeed buildEmployee(
            Random random,
            long randomSeed,
            int index,
            List<DepartmentSeed> departments
    ) {
        CountryProfile country = COUNTRIES[index % COUNTRIES.length];
        int deptIndex = (index + random.nextInt(DEPARTMENTS.length)) % DEPARTMENTS.length;
        DepartmentSeed department = departments.get(deptIndex);
        DeptDef deptDef = DEPARTMENTS[deptIndex];

        int seniority = pickSeniority(random, index);
        String designation = deptDef.designations()[Math.min(seniority, deptDef.designations().length - 1)];

        String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        String employeeCode = String.format(Locale.ROOT, "EMP%05d", index);
        String email = String.format(
                Locale.ROOT,
                "%s.%s.%05d@acme.paylens.test",
                slug(firstName),
                slug(lastName),
                index
        );

        LocalDate joiningDate = LocalDate.of(2015, 1, 1).plusDays(random.nextInt(365 * 9));
        String status = STATUSES[random.nextInt(STATUSES.length)];

        BigDecimal salary = salaryFor(country, seniority, random);
        List<SalarySeed> salaries = new ArrayList<>(2);
        salaries.add(new SalarySeed(
                deterministicUuid(randomSeed, "sal-hire", index),
                salary,
                country.currency(),
                joiningDate
        ));

        // ~25% of active/on-leave staff have a raise for history / analytics
        if (!"TERMINATED".equals(status) && index % 4 == 0) {
            LocalDate raiseDate = joiningDate.plusYears(1 + (index % 3));
            if (raiseDate.isBefore(LocalDate.of(2025, 1, 1))) {
                BigDecimal raised = salary.multiply(BigDecimal.valueOf(1.08 + (seniority * 0.01)))
                        .setScale(2, RoundingMode.HALF_UP);
                salaries.add(new SalarySeed(
                        deterministicUuid(randomSeed, "sal-raise", index),
                        raised,
                        country.currency(),
                        raiseDate
                ));
            }
        }

        return new EmployeeSeed(
                deterministicUuid(randomSeed, "emp", index),
                employeeCode,
                firstName,
                lastName,
                email,
                country.code(),
                department.id(),
                designation,
                status,
                joiningDate,
                List.copyOf(salaries)
        );
    }

    /**
     * Bias toward mid levels; still includes juniors and seniors for pay bands.
     */
    private static int pickSeniority(Random random, int index) {
        int roll = (index + random.nextInt(100)) % 100;
        if (roll < 20) {
            return 0;
        }
        if (roll < 55) {
            return 1;
        }
        if (roll < 80) {
            return 2;
        }
        if (roll < 93) {
            return 3;
        }
        return 4;
    }

    private static BigDecimal salaryFor(CountryProfile country, int seniority, Random random) {
        BigDecimal span = country.maxSalary().subtract(country.minSalary());
        double seniorityFactor = Math.min(1.0, 0.15 + seniority * 0.2);
        double jitter = 0.9 + (random.nextDouble() * 0.2);
        return country.minSalary()
                .add(span.multiply(BigDecimal.valueOf(seniorityFactor * jitter)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static UUID deterministicUuid(long randomSeed, String namespace, int index) {
        return UUID.nameUUIDFromBytes((randomSeed + ":" + namespace + ":" + index).getBytes());
    }

    private record CountryProfile(String code, String currency, BigDecimal minSalary, BigDecimal maxSalary) {
    }

    private record DeptDef(String code, String name, String[] designations) {
    }
}
