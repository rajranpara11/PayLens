package com.paylens.seed;

import com.paylens.seed.SeedModels.DepartmentSeed;
import com.paylens.seed.SeedModels.EmployeeSeed;
import com.paylens.seed.SeedModels.SalarySeed;
import com.paylens.seed.SeedModels.SeedDataset;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC bulk seeder. Commits per batch so a 10k seed does not hold one giant transaction.
 */
@Component
public class JdbcBatchSeeder {

    private static final Logger log = LoggerFactory.getLogger(JdbcBatchSeeder.class);

    private static final String INSERT_DEPARTMENT = """
            INSERT INTO department (id, code, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String INSERT_EMPLOYEE = """
            INSERT INTO employee (
                id, employee_code, first_name, last_name, email, country,
                department_id, designation, employment_status, joining_date, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_SALARY = """
            INSERT INTO salary (id, employee_id, annual_salary, currency, effective_from, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcBatchSeeder(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void seed(SeedDataset dataset, int batchSize) {
        Instant stamped = Instant.parse("2024-01-01T00:00:00Z");
        Timestamp ts = Timestamp.from(stamped);
        int size = Math.max(1, batchSize);

        transactionTemplate.executeWithoutResult(status -> insertDepartments(dataset.departments(), ts));
        insertEmployees(dataset.employees(), ts, size);
        insertSalaries(dataset.employees(), ts, size);
        log.info(
                "Seed persisted: {} departments, {} employees, {} salary rows",
                dataset.departments().size(),
                dataset.employees().size(),
                dataset.employees().stream().mapToInt(e -> e.salaries().size()).sum()
        );
    }

    private void insertDepartments(List<DepartmentSeed> departments, Timestamp ts) {
        jdbcTemplate.batchUpdate(INSERT_DEPARTMENT, departments, departments.size(), (ps, dept) -> {
            ps.setObject(1, dept.id());
            ps.setString(2, dept.code());
            ps.setString(3, dept.name());
            ps.setTimestamp(4, ts);
            ps.setTimestamp(5, ts);
        });
    }

    private void insertEmployees(List<EmployeeSeed> employees, Timestamp ts, int batchSize) {
        for (int start = 0; start < employees.size(); start += batchSize) {
            int end = Math.min(start + batchSize, employees.size());
            List<EmployeeSeed> chunk = employees.subList(start, end);
            transactionTemplate.executeWithoutResult(status ->
                    jdbcTemplate.batchUpdate(INSERT_EMPLOYEE, chunk, chunk.size(), (ps, employee) -> {
                        ps.setObject(1, employee.id());
                        ps.setString(2, employee.employeeCode());
                        ps.setString(3, employee.firstName());
                        ps.setString(4, employee.lastName());
                        ps.setString(5, employee.email());
                        ps.setString(6, employee.country());
                        ps.setObject(7, employee.departmentId());
                        ps.setString(8, employee.designation());
                        ps.setString(9, employee.employmentStatus());
                        ps.setDate(10, Date.valueOf(employee.joiningDate()));
                        ps.setTimestamp(11, ts);
                        ps.setTimestamp(12, ts);
                    })
            );
            log.info("Inserted employees {}-{}", start + 1, start + chunk.size());
        }
    }

    private void insertSalaries(List<EmployeeSeed> employees, Timestamp ts, int batchSize) {
        List<SalaryRow> buffer = new ArrayList<>(batchSize);
        int inserted = 0;
        for (EmployeeSeed employee : employees) {
            for (SalarySeed salary : employee.salaries()) {
                buffer.add(new SalaryRow(employee.id(), salary));
                if (buffer.size() >= batchSize) {
                    flushSalaries(buffer, ts);
                    inserted += buffer.size();
                    log.info("Inserted salaries {}-{}", inserted - buffer.size() + 1, inserted);
                    buffer.clear();
                }
            }
        }
        if (!buffer.isEmpty()) {
            int from = inserted + 1;
            flushSalaries(buffer, ts);
            inserted += buffer.size();
            log.info("Inserted salaries {}-{}", from, inserted);
        }
    }

    private void flushSalaries(List<SalaryRow> rows, Timestamp ts) {
        List<SalaryRow> chunk = List.copyOf(rows);
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.batchUpdate(INSERT_SALARY, chunk, chunk.size(), (ps, row) -> {
                    SalarySeed salary = row.salary();
                    ps.setObject(1, salary.id());
                    ps.setObject(2, row.employeeId());
                    ps.setBigDecimal(3, salary.annualSalary());
                    ps.setString(4, salary.currency());
                    ps.setDate(5, Date.valueOf(salary.effectiveFrom()));
                    ps.setTimestamp(6, ts);
                    ps.setTimestamp(7, ts);
                })
        );
    }

    private record SalaryRow(UUID employeeId, SalarySeed salary) {
    }
}
