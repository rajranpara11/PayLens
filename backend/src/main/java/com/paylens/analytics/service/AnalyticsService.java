package com.paylens.analytics.service;

import com.paylens.analytics.dto.AnalyticsOverviewResponse;
import com.paylens.analytics.dto.CountryHeadcountRow;
import com.paylens.analytics.dto.CountryPayrollRow;
import com.paylens.analytics.dto.DepartmentSalaryRow;
import com.paylens.analytics.dto.DesignationSalaryRow;
import com.paylens.analytics.dto.SalaryDistributionBucket;
import com.paylens.analytics.repository.AnalyticsRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    public static final String CURRENCY_NOTE =
            "Compensation metrics are grouped by currency and must not be summed or averaged across currencies.";

    private final AnalyticsRepository analyticsRepository;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository analyticsRepository, Clock clock) {
        this.analyticsRepository = analyticsRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview() {
        LocalDate asOf = today();
        return new AnalyticsOverviewResponse(
                analyticsRepository.countAllEmployees(),
                analyticsRepository.countEmployedEmployees(),
                analyticsRepository.compensationByCurrency(asOf),
                CURRENCY_NOTE
        );
    }

    @Transactional(readOnly = true)
    public List<CountryPayrollRow> payrollByCountry() {
        return analyticsRepository.payrollByCountry(today());
    }

    @Transactional(readOnly = true)
    public List<DepartmentSalaryRow> salaryByDepartment() {
        return analyticsRepository.salaryByDepartment(today());
    }

    @Transactional(readOnly = true)
    public List<DesignationSalaryRow> salaryByDesignation() {
        return analyticsRepository.salaryByDesignation(today());
    }

    @Transactional(readOnly = true)
    public List<SalaryDistributionBucket> salaryDistribution() {
        return analyticsRepository.salaryDistribution(today());
    }

    @Transactional(readOnly = true)
    public List<CountryHeadcountRow> employeeCountByCountry() {
        return analyticsRepository.employeeCountByCountry();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
