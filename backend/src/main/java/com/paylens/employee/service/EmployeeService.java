package com.paylens.employee.service;

import com.paylens.common.exception.ConflictException;
import com.paylens.common.exception.NotFoundException;
import com.paylens.common.exception.ValidationException;
import com.paylens.common.validation.CountryCodes;
import com.paylens.employee.dto.CreateEmployeeRequest;
import com.paylens.employee.dto.EmployeeResponse;
import com.paylens.employee.dto.EmployeeSearchCriteria;
import com.paylens.employee.dto.UpdateEmployeeRequest;
import com.paylens.employee.entity.Department;
import com.paylens.employee.entity.Employee;
import com.paylens.employee.entity.EmploymentStatus;
import com.paylens.employee.repository.DepartmentRepository;
import com.paylens.employee.repository.EmployeeRepository;
import com.paylens.employee.repository.EmployeeSpecifications;
import com.paylens.salary.entity.Salary;
import com.paylens.salary.repository.SalaryRepository;
import com.paylens.salary.service.SalaryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final SalaryRepository salaryRepository;
    private final SalaryService salaryService;
    private final Clock clock;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            SalaryRepository salaryRepository,
            SalaryService salaryService,
            Clock clock
    ) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.salaryRepository = salaryRepository;
        this.salaryService = salaryService;
        this.clock = clock;
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
        Salary current = salaryService.findCurrent(id).orElse(null);
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
        Salary salary = salaryService.apply(saved, request.salary());
        return EmployeeMapper.toResponse(saved, salary);
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
        Salary salary;
        if (request.salary() != null) {
            salary = salaryService.apply(employee, request.salary());
        } else {
            salary = salaryService.findCurrent(id).orElse(null);
        }
        return EmployeeMapper.toResponse(employee, salary);
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
                    .orElseThrow(() -> new ValidationException("Unknown country", "country"));
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
            spec = spec.and(EmployeeSpecifications.currentCurrencyEquals(
                    SalaryService.requireCurrency(criteria.currency()),
                    LocalDate.now(clock)
            ));
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
                .orElseThrow(() -> new ValidationException("Unknown country", "country")));
        employee.setDepartment(resolveDepartment(department));
        employee.setDesignation(designation.trim());
        employee.setEmploymentStatus(status);
        employee.setJoiningDate(joiningDate);
    }

    private Department resolveDepartment(String department) {
        String value = department.trim();
        return departmentRepository.findByCodeIgnoreCase(value)
                .or(() -> departmentRepository.findByNameIgnoreCase(value))
                .orElseThrow(() -> new ValidationException("Unknown department", "department"));
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
        LocalDate today = LocalDate.now(clock);
        return salaryRepository.findCurrentByEmployeeIds(employeeIds, today).stream()
                .collect(Collectors.toMap(
                        salary -> salary.getEmployee().getId(),
                        salary -> salary,
                        (left, right) -> left.getEffectiveFrom().isAfter(right.getEffectiveFrom()) ? left : right
                ));
    }

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "lastName",
            "firstName",
            "employeeCode",
            "joiningDate",
            "country",
            "designation",
            "employmentStatus",
            "department.name"
    );

    private static Pageable clamp(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        if (size < 1) {
            size = 25;
        }
        Sort sort = pageable.getSort();
        if (sort.isSorted()) {
            List<Sort.Order> safeOrders = sort.stream()
                    .filter(order -> ALLOWED_SORT_PROPERTIES.contains(order.getProperty()))
                    .toList();
            sort = safeOrders.isEmpty()
                    ? Sort.by(Sort.Direction.ASC, "lastName")
                    : Sort.by(safeOrders);
        }
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }

    private static EmploymentStatus parseStatus(String raw, EmploymentStatus defaultStatus) {
        if (raw == null || raw.isBlank()) {
            if (defaultStatus == null) {
                throw new ValidationException("employmentStatus is required", "employmentStatus");
            }
            return defaultStatus;
        }
        try {
            return EmploymentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid employmentStatus", "employmentStatus");
        }
    }

    private static String normalizeCode(String employeeCode) {
        return employeeCode.trim();
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
