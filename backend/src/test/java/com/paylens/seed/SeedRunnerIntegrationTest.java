package com.paylens.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.paylens.employee.repository.EmployeeRepository;
import com.paylens.salary.repository.SalaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:paylens_seed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "paylens.seed.enabled=true",
        "paylens.seed.employee-count=250",
        "paylens.seed.random-seed=42",
        "paylens.seed.batch-size=100"
})
class SeedRunnerIntegrationTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryRepository salaryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SeedRunner seedRunner;

    @Test
    void seedsExactCountWithSalariesAndNoDuplicateCodes() {
        assertThat(employeeRepository.count()).isEqualTo(250);
        assertThat(salaryRepository.count()).isGreaterThanOrEqualTo(250);

        Integer distinctCodes = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT employee_code) FROM employee",
                Integer.class
        );
        Integer distinctEmails = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT email) FROM employee",
                Integer.class
        );
        assertThat(distinctCodes).isEqualTo(250);
        assertThat(distinctEmails).isEqualTo(250);

        Integer departments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM department", Integer.class);
        assertThat(departments).isEqualTo(10);
    }

    @Test
    void secondRunIsIdempotent() {
        long before = employeeRepository.count();
        seedRunner.run(new DefaultApplicationArguments());
        assertThat(employeeRepository.count()).isEqualTo(before);
    }
}
