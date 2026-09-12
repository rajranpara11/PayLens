package com.paylens.salary.repository;

import com.paylens.salary.entity.Salary;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryRepository extends JpaRepository<Salary, UUID> {

    Optional<Salary> findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID employeeId,
            LocalDate onDate
    );

    Optional<Salary> findByEmployee_IdAndEffectiveFrom(UUID employeeId, LocalDate effectiveFrom);

    List<Salary> findByEmployee_IdOrderByEffectiveFromDesc(UUID employeeId);

    List<Salary> findByEmployee_IdIn(Collection<UUID> employeeIds);
}
