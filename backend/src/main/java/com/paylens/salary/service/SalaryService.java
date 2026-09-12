package com.paylens.salary.service;

import com.paylens.common.exception.NotFoundException;
import com.paylens.common.exception.ValidationException;
import com.paylens.common.validation.Currencies;
import com.paylens.employee.dto.SalaryRequest;
import com.paylens.employee.dto.SalaryResponse;
import com.paylens.employee.entity.Employee;
import com.paylens.employee.repository.EmployeeRepository;
import com.paylens.salary.dto.SalaryMapper;
import com.paylens.salary.entity.Salary;
import com.paylens.salary.repository.SalaryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalaryService {

    private final EmployeeRepository employeeRepository;
    private final SalaryRepository salaryRepository;
    private final Clock clock;

    public SalaryService(
            EmployeeRepository employeeRepository,
            SalaryRepository salaryRepository,
            Clock clock
    ) {
        this.employeeRepository = employeeRepository;
        this.salaryRepository = salaryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SalaryResponse getCurrent(UUID employeeId) {
        requireEmployee(employeeId);
        return findCurrent(employeeId)
                .map(SalaryMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("No current salary"));
    }

    @Transactional(readOnly = true)
    public List<SalaryResponse> history(UUID employeeId) {
        requireEmployee(employeeId);
        return salaryRepository.findByEmployee_IdOrderByEffectiveFromDesc(employeeId).stream()
                .map(SalaryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Salary> findCurrent(UUID employeeId) {
        return salaryRepository.findFirstByEmployee_IdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                employeeId,
                today()
        );
    }

    @Transactional
    public SalaryResponse upsert(UUID employeeId, SalaryRequest request) {
        Employee employee = requireEmployee(employeeId);
        return SalaryMapper.toResponse(apply(employee, request));
    }

    @Transactional
    public Salary apply(Employee employee, SalaryRequest request) {
        BigDecimal amount = requirePositiveAmount(request.annualSalary());
        String currency = requireCurrency(request.currency());
        if (request.effectiveFrom() == null) {
            throw new ValidationException("effectiveFrom is required");
        }

        Optional<Salary> existing = salaryRepository.findByEmployee_IdAndEffectiveFrom(
                employee.getId(),
                request.effectiveFrom()
        );
        if (existing.isPresent()) {
            Salary salary = existing.get();
            salary.setAnnualSalary(amount);
            salary.setCurrency(currency);
            return salaryRepository.save(salary);
        }

        Salary salary = new Salary();
        salary.setEmployee(employee);
        salary.setAnnualSalary(amount);
        salary.setCurrency(currency);
        salary.setEffectiveFrom(request.effectiveFrom());
        return salaryRepository.save(salary);
    }

    public static BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("annualSalary must be greater than 0");
        }
        return amount;
    }

    public static String requireCurrency(String raw) {
        return Currencies.normalize(raw)
                .orElseThrow(() -> new ValidationException("Unsupported currency", "currency"));
    }

    private Employee requireEmployee(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
