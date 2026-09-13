package com.paylens.salary.repository;

import com.paylens.salary.entity.Salary;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalaryRepository extends JpaRepository<Salary, UUID> {

    Optional<Salary> findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID employeeId,
            LocalDate onDate
    );

    Optional<Salary> findByEmployee_IdAndEffectiveFrom(UUID employeeId, LocalDate effectiveFrom);

    List<Salary> findByEmployee_IdOrderByEffectiveFromDesc(UUID employeeId);

    /**
     * Current salary only (latest {@code effectiveFrom <= onDate}) for a page of employees.
     * Avoids loading full salary history into memory for list hydration.
     */
    @Query("""
            SELECT s FROM Salary s
            WHERE s.employee.id IN :employeeIds
              AND s.effectiveFrom = (
                  SELECT MAX(s2.effectiveFrom) FROM Salary s2
                  WHERE s2.employee.id = s.employee.id
                    AND s2.effectiveFrom <= :onDate
              )
            """)
    List<Salary> findCurrentByEmployeeIds(
            @Param("employeeIds") Collection<UUID> employeeIds,
            @Param("onDate") LocalDate onDate
    );
}
