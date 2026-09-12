package com.paylens.employee.service;

import com.paylens.common.exception.ConflictException;
import com.paylens.common.exception.NotFoundException;
import com.paylens.common.exception.ValidationException;
import com.paylens.common.validation.CountryCodes;
import com.paylens.employee.dto.CreateEmployeeRequest;
import com.paylens.employee.dto.EmployeeResponse;
import com.paylens.employee.dto.EmployeeSearchCriteria;
import com.paylens.employee.dto.SalaryRequest;
import com.paylens.employee.dto.UpdateEmployeeRequest;
import com.paylens.employee.entity.Department;
import com.paylens.employee.entity.Employee;
import com.paylens.employee.entity.EmploymentStatus;
import com.paylens.employee.repository.DepartmentRepository;
import com.paylens.employee.repository.EmployeeRepository;
import com.paylens.employee.repository.EmployeeSpecifications;
import com.paylens.salary.entity.Salary;
import com.paylens.salary.repository.SalaryRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final SalaryRepository salaryRepository;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            SalaryRepository salaryRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.salaryRepository = salaryRepository;
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> list(EmployeeSearchCriteria criteria, Pageable pageable) {
        Pageable safePageable = clamp(pageable);
        Specification<Employee> spec = buildSpec(criteria);
        Page<Employee> page = employeeRepository.findAll(spec, safePageable);
        Map<UUID, Salary> currentSalaries = currentSalariesByEmployeeId(
                page.getContent().stream().map(Employee::getId).toList()
        );
        return page.map(employee -> EmployeeMapper.toResponse(employee, currentSalaries.get(employee.getId())));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        Salary current = salaryRepository.findFirstByEmployee_IdOrderByEffectiveFromDesc(id).orElse(null);
        return EmployeeMapper.toResponse(employee, current);
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        String employeeCode = normalizeCode(request.employeeCode());
        String email = normalizeEmail(request.email());
        assertUniqueOnCreate(employeeCode, email);

        Employee employee = new Employee();
        applyIdentity(
                employee,
                employeeCode,
                request.firstName(),
                request.lastName(),
                email,
                request.country(),
                request.department(),
                request.designation(),
                parseStatus(request.employmentStatus(), EmploymentStatus.ACTIVE),
                request.joiningDate()
        );
        Employee saved = employeeRepository.save(employee);
        saveSalary(saved, request.salary(), true);
        return get(saved.getId());
    }

    @Transactional
    public EmployeeResponse update(UUID id, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        String employeeCode = normalizeCode(request.employeeCode());
        String email = normalizeEmail(request.email());
        assertUniqueOnUpdate(id, employeeCode, email);

        applyIdentity(
                employee,
                employeeCode,
                request.firstName(),
                request.lastName(),
                email,
                request.country(),
                request.department(),
                request.designation(),
                parseStatus(request.employmentStatus(), null),
                request.joiningDate()
        );
        employeeRepository.save(employee);
        if (request.salary() != null) {
            saveSalary(employee, request.salary(), false);
        }
        return get(id);
    }

    @Transactional
    public void deactivate(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        employee.setEmploymentStatus(EmploymentStatus.TERMINATED);
        employeeRepository.save(employee);
    }

    private Specification<Employee> buildSpec(EmployeeSearchCriteria criteria) {
        Specification<Employee> spec = Specification.unrestricted();
        if (hasText(criteria.search())) {
            spec = spec.and(EmployeeSpecifications.search(criteria.search().trim()));
        }
        if (hasText(criteria.country())) {
            String country = CountryCodes.toIsoCode(criteria.country())
                    .orElseThrow(() -> new ValidationException("Unknown country"));
            spec = spec.and(EmployeeSpecifications.countryEquals(country));
        }
        if (hasText(criteria.department())) {
            spec = spec.and(EmployeeSpecifications.departmentMatches(criteria.department().trim()));
        }
        if (hasText(criteria.designation())) {
            spec = spec.and(EmployeeSpecifications.designationEquals(criteria.designation().trim()));
        }
        if (hasText(criteria.employmentStatus())) {
            spec = spec.and(EmployeeSpecifications.statusEquals(parseStatus(criteria.employmentStatus(), null)));
        }
        if (hasText(criteria.currency())) {
            spec = spec.and(EmployeeSpecifications.currentCurrencyEquals(normalizeCurrency(criteria.currency())));
        }
        return spec;
    }

    private void applyIdentity(
            Employee employee,
            String employeeCode,
            String firstName,
            String lastName,
            String email,
            String country,
            String department,
            String designation,
            EmploymentStatus status,
            java.time.LocalDate joiningDate
    ) {
        employee.setEmployeeCode(employeeCode);
        employee.setFirstName(firstName.trim());
        employee.setLastName(lastName.trim());
        employee.setEmail(email);
        employee.setCountry(CountryCodes.toIsoCode(country)
                .orElseThrow(() -> new ValidationException("Unknown country")));
        employee.setDepartment(resolveDepartment(department));
        employee.setDesignation(designation.trim());
        employee.setEmploymentStatus(status);
        employee.setJoiningDate(joiningDate);
    }

    private void saveSalary(Employee employee, SalaryRequest request, boolean requiredNew) {
        BigDecimal amount = request.annualSalary();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("annualSalary must be greater than 0");
        }
        String currency = normalizeCurrency(request.currency());
        Optional<Salary> existing = salaryRepository.findByEmployee_IdAndEffectiveFrom(
                employee.getId(),
                request.effectiveFrom()
        );
        if (existing.isPresent()) {
            if (requiredNew) {
                throw new ConflictException("A salary already exists for this effective date");
            }
            Salary salary = existing.get();
            salary.setAnnualSalary(amount);
            salary.setCurrency(currency);
            salaryRepository.save(salary);
            return;
        }
        Salary salary = new Salary();
        salary.setEmployee(employee);
        salary.setAnnualSalary(amount);
        salary.setCurrency(currency);
        salary.setEffectiveFrom(request.effectiveFrom());
        salaryRepository.save(salary);
    }

    private Department resolveDepartment(String department) {
        String value = department.trim();
        return departmentRepository.findByCodeIgnoreCase(value)
                .or(() -> departmentRepository.findByNameIgnoreCase(value))
                .orElseThrow(() -> new ValidationException("Unknown department"));
    }

    private void assertUniqueOnCreate(String employeeCode, String email) {
        if (employeeRepository.existsByEmployeeCodeIgnoreCase(employeeCode)) {
            throw new ConflictException("employeeCode already exists");
        }
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("email already exists");
        }
    }

    private void assertUniqueOnUpdate(UUID id, String employeeCode, String email) {
        if (employeeRepository.existsByEmployeeCodeIgnoreCaseAndIdNot(employeeCode, id)) {
            throw new ConflictException("employeeCode already exists");
        }
        if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflictException("email already exists");
        }
    }

    private Map<UUID, Salary> currentSalariesByEmployeeId(List<UUID> employeeIds) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        return salaryRepository.findByEmployee_IdIn(employeeIds).stream()
                .collect(Collectors.groupingBy(salary -> salary.getEmployee().getId()))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .max(Comparator.comparing(Salary::getEffectiveFrom))
                                .orElseThrow()
                ));
    }

    private static EmploymentStatus parseStatus(String raw, EmploymentStatus defaultStatus) {
        if (raw == null || raw.isBlank()) {
            if (defaultStatus == null) {
                throw new ValidationException("employmentStatus is required");
            }
            return defaultStatus;
        }
        try {
            return EmploymentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid employmentStatus");
        }
    }

    private static String normalizeCurrency(String currency) {
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3 || !normalized.chars().allMatch(Character::isLetter)) {
            throw new ValidationException("currency must be a 3-letter ISO 4217 code");
        }
        return normalized;
    }

    private static String normalizeCode(String employeeCode) {
        return employeeCode.trim();
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static Pageable clamp(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        if (size < 1) {
            size = 25;
        }
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
