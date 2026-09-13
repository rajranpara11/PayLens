package com.paylens.employee.repository;

import com.paylens.employee.entity.Employee;
import com.paylens.employee.entity.EmploymentStatus;
import com.paylens.salary.entity.Salary;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    public static Specification<Employee> search(String search) {
        String pattern = "%" + escape(search.toLowerCase()) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
                cb.like(cb.lower(root.get("lastName")), pattern, '\\'),
                cb.like(cb.lower(root.get("email")), pattern, '\\'),
                cb.like(cb.lower(root.get("employeeCode")), pattern, '\\')
        );
    }

    public static Specification<Employee> countryEquals(String country) {
        return (root, query, cb) -> cb.equal(root.get("country"), country);
    }

    public static Specification<Employee> departmentMatches(String department) {
        String value = department.toLowerCase();
        return (root, query, cb) -> {
            Join<Object, Object> dept = root.join("department", JoinType.INNER);
            return cb.or(
                    cb.equal(cb.lower(dept.get("code")), value),
                    cb.equal(cb.lower(dept.get("name")), value)
            );
        };
    }

    public static Specification<Employee> designationEquals(String designation) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("designation")), designation.toLowerCase());
    }

    public static Specification<Employee> statusEquals(EmploymentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("employmentStatus"), status);
    }

    /**
     * Matches employees whose <em>current</em> salary (latest {@code effectiveFrom <= onDate})
     * uses the given currency. Uses an anti-join instead of nested max + exists subqueries.
     */
    public static Specification<Employee> currentCurrencyEquals(String currency, LocalDate onDate) {
        return (root, query, cb) -> {
            Subquery<Integer> currentCurrency = query.subquery(Integer.class);
            Root<Salary> salary = currentCurrency.from(Salary.class);

            Subquery<Integer> hasLater = currentCurrency.subquery(Integer.class);
            Root<Salary> newer = hasLater.from(Salary.class);
            hasLater.select(cb.literal(1));
            hasLater.where(
                    cb.equal(newer.get("employee"), root),
                    cb.greaterThan(newer.get("effectiveFrom"), salary.get("effectiveFrom")),
                    cb.lessThanOrEqualTo(newer.get("effectiveFrom"), onDate)
            );

            currentCurrency.select(cb.literal(1));
            currentCurrency.where(
                    cb.equal(salary.get("employee"), root),
                    cb.equal(salary.get("currency"), currency),
                    cb.lessThanOrEqualTo(salary.get("effectiveFrom"), onDate),
                    cb.not(cb.exists(hasLater))
            );
            return cb.exists(currentCurrency);
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
