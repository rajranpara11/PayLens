package com.paylens.analytics.repository;

import com.paylens.analytics.dto.CountryHeadcountRow;
import com.paylens.analytics.dto.CountryPayrollRow;
import com.paylens.analytics.dto.CurrencyCompensationStats;
import com.paylens.analytics.dto.DepartmentSalaryRow;
import com.paylens.analytics.dto.DesignationSalaryRow;
import com.paylens.analytics.dto.SalaryDistributionBucket;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only analytics. Aggregations run in SQL over current salaries
 * (latest {@code effective_from <= asOf} per employee). Money is always grouped by currency.
 */
@Repository
public class AnalyticsRepository {

    private static final String CURRENT_SALARY_CTE = """
            WITH latest AS (
                SELECT employee_id, MAX(effective_from) AS effective_from
                FROM salary
                WHERE effective_from <= ?
                GROUP BY employee_id
            ),
            current_salary AS (
                SELECT
                    s.employee_id,
                    s.annual_salary,
                    s.currency,
                    e.country,
                    e.department_id,
                    e.designation
                FROM salary s
                INNER JOIN latest l
                    ON l.employee_id = s.employee_id
                   AND l.effective_from = s.effective_from
                INNER JOIN employee e ON e.id = s.employee_id
                WHERE e.employment_status IN ('ACTIVE', 'ON_LEAVE')
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public EmployeeCounts employeeCounts() {
        EmployeeCounts counts = jdbcTemplate.queryForObject(
                """
                SELECT
                    COUNT(*) AS total_count,
                    COALESCE(SUM(CASE WHEN employment_status IN ('ACTIVE', 'ON_LEAVE') THEN 1 ELSE 0 END), 0)
                        AS employed_count
                FROM employee
                """,
                (rs, rowNum) -> new EmployeeCounts(rs.getLong("total_count"), rs.getLong("employed_count"))
        );
        return counts == null ? new EmployeeCounts(0L, 0L) : counts;
    }

    public long countAllEmployees() {
        return employeeCounts().total();
    }

    public long countEmployedEmployees() {
        return employeeCounts().employed();
    }

    public List<CurrencyCompensationStats> compensationByCurrency(LocalDate asOf) {
        String sql = CURRENT_SALARY_CTE + """
                SELECT
                    currency,
                    COUNT(*) AS employee_count,
                    AVG(annual_salary) AS average_salary,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY annual_salary) AS median_salary,
                    MIN(annual_salary) AS min_salary,
                    MAX(annual_salary) AS max_salary,
                    SUM(annual_salary) AS total_payroll
                FROM current_salary
                GROUP BY currency
                ORDER BY currency
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CurrencyCompensationStats(
                rs.getString("currency").trim(),
                rs.getLong("employee_count"),
                money(rs.getBigDecimal("average_salary")),
                money(rs.getBigDecimal("median_salary")),
                money(rs.getBigDecimal("min_salary")),
                money(rs.getBigDecimal("max_salary")),
                money(rs.getBigDecimal("total_payroll"))
        ), Date.valueOf(asOf));
    }

    public List<CountryPayrollRow> payrollByCountry(LocalDate asOf) {
        String sql = CURRENT_SALARY_CTE + """
                SELECT
                    country,
                    currency,
                    COUNT(*) AS employee_count,
                    SUM(annual_salary) AS total_payroll,
                    AVG(annual_salary) AS average_salary
                FROM current_salary
                GROUP BY country, currency
                ORDER BY country, currency
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CountryPayrollRow(
                rs.getString("country").trim(),
                rs.getString("currency").trim(),
                rs.getLong("employee_count"),
                money(rs.getBigDecimal("total_payroll")),
                money(rs.getBigDecimal("average_salary"))
        ), Date.valueOf(asOf));
    }

    public List<DepartmentSalaryRow> salaryByDepartment(LocalDate asOf) {
        String sql = CURRENT_SALARY_CTE + """
                SELECT
                    d.code AS department_code,
                    d.name AS department_name,
                    cs.currency,
                    COUNT(*) AS employee_count,
                    AVG(cs.annual_salary) AS average_salary,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY cs.annual_salary) AS median_salary,
                    MIN(cs.annual_salary) AS min_salary,
                    MAX(cs.annual_salary) AS max_salary,
                    SUM(cs.annual_salary) AS total_payroll
                FROM current_salary cs
                INNER JOIN department d ON d.id = cs.department_id
                GROUP BY d.code, d.name, cs.currency
                ORDER BY d.code, cs.currency
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DepartmentSalaryRow(
                rs.getString("department_code"),
                rs.getString("department_name"),
                rs.getString("currency").trim(),
                rs.getLong("employee_count"),
                money(rs.getBigDecimal("average_salary")),
                money(rs.getBigDecimal("median_salary")),
                money(rs.getBigDecimal("min_salary")),
                money(rs.getBigDecimal("max_salary")),
                money(rs.getBigDecimal("total_payroll"))
        ), Date.valueOf(asOf));
    }

    public List<DesignationSalaryRow> salaryByDesignation(LocalDate asOf) {
        String sql = CURRENT_SALARY_CTE + """
                SELECT
                    designation,
                    currency,
                    COUNT(*) AS employee_count,
                    AVG(annual_salary) AS average_salary,
                    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY annual_salary) AS median_salary,
                    MIN(annual_salary) AS min_salary,
                    MAX(annual_salary) AS max_salary,
                    SUM(annual_salary) AS total_payroll
                FROM current_salary
                GROUP BY designation, currency
                ORDER BY designation, currency
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DesignationSalaryRow(
                rs.getString("designation"),
                rs.getString("currency").trim(),
                rs.getLong("employee_count"),
                money(rs.getBigDecimal("average_salary")),
                money(rs.getBigDecimal("median_salary")),
                money(rs.getBigDecimal("min_salary")),
                money(rs.getBigDecimal("max_salary")),
                money(rs.getBigDecimal("total_payroll"))
        ), Date.valueOf(asOf));
    }

    public List<SalaryDistributionBucket> salaryDistribution(LocalDate asOf) {
        String sql = CURRENT_SALARY_CTE + """
                ,
                bounds AS (
                    SELECT
                        currency,
                        MIN(annual_salary) AS lo,
                        MAX(annual_salary) AS hi
                    FROM current_salary
                    GROUP BY currency
                ),
                bucketed AS (
                    SELECT
                        cs.currency,
                        CASE
                            WHEN b.lo = b.hi THEN 1
                            WHEN cs.annual_salary < b.lo + (b.hi - b.lo) * 0.2 THEN 1
                            WHEN cs.annual_salary < b.lo + (b.hi - b.lo) * 0.4 THEN 2
                            WHEN cs.annual_salary < b.lo + (b.hi - b.lo) * 0.6 THEN 3
                            WHEN cs.annual_salary < b.lo + (b.hi - b.lo) * 0.8 THEN 4
                            ELSE 5
                        END AS bucket,
                        cs.annual_salary
                    FROM current_salary cs
                    INNER JOIN bounds b ON b.currency = cs.currency
                )
                SELECT
                    currency,
                    bucket,
                    COUNT(*) AS employee_count,
                    MIN(annual_salary) AS band_min,
                    MAX(annual_salary) AS band_max
                FROM bucketed
                GROUP BY currency, bucket
                ORDER BY currency, bucket
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SalaryDistributionBucket(
                rs.getString("currency").trim(),
                rs.getInt("bucket"),
                rs.getLong("employee_count"),
                money(rs.getBigDecimal("band_min")),
                money(rs.getBigDecimal("band_max"))
        ), Date.valueOf(asOf));
    }

    public List<CountryHeadcountRow> employeeCountByCountry() {
        return jdbcTemplate.query(
                """
                SELECT country, COUNT(*) AS employee_count
                FROM employee
                WHERE employment_status IN ('ACTIVE', 'ON_LEAVE')
                GROUP BY country
                ORDER BY country
                """,
                (rs, rowNum) -> new CountryHeadcountRow(
                        rs.getString("country").trim(),
                        rs.getLong("employee_count")
                )
        );
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public record EmployeeCounts(long total, long employed) {
    }
}
