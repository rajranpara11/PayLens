package com.paylens.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paylens.analytics.dto.AnalyticsOverviewResponse;
import com.paylens.analytics.dto.CurrencyCompensationStats;
import com.paylens.analytics.repository.AnalyticsRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AnalyticsRepository analyticsRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2024-06-15T00:00:00Z"), ZoneOffset.UTC);
        analyticsService = new AnalyticsService(analyticsRepository, clock);
    }

    @Test
    void overviewKeepsCurrenciesSeparateAndDocumentsRule() {
        when(analyticsRepository.employeeCounts()).thenReturn(new AnalyticsRepository.EmployeeCounts(100L, 90L));
        when(analyticsRepository.compensationByCurrency(LocalDate.of(2024, 6, 15))).thenReturn(List.of(
                new CurrencyCompensationStats(
                        "INR", 50, bd("1000000"), bd("900000"), bd("500000"), bd("2000000"), bd("50000000")
                ),
                new CurrencyCompensationStats(
                        "USD", 40, bd("100000"), bd("95000"), bd("60000"), bd("200000"), bd("4000000")
                )
        ));

        AnalyticsOverviewResponse overview = analyticsService.overview();

        assertThat(overview.totalEmployees()).isEqualTo(100);
        assertThat(overview.employedEmployees()).isEqualTo(90);
        assertThat(overview.compensationByCurrency()).hasSize(2);
        assertThat(overview.compensationByCurrency())
                .extracting(CurrencyCompensationStats::currency)
                .containsExactly("INR", "USD");
        assertThat(overview.currencyNote()).contains("must not be summed");
        // No single blended average/total field exists on the response type.
        verify(analyticsRepository).employeeCounts();
        verify(analyticsRepository).compensationByCurrency(LocalDate.of(2024, 6, 15));
    }

    @Test
    void readOnlyDelegatesUseAsOfToday() {
        LocalDate asOf = LocalDate.of(2024, 6, 15);
        when(analyticsRepository.payrollByCountry(asOf)).thenReturn(List.of());
        when(analyticsRepository.salaryByDepartment(asOf)).thenReturn(List.of());
        when(analyticsRepository.salaryByDesignation(asOf)).thenReturn(List.of());
        when(analyticsRepository.salaryDistribution(asOf)).thenReturn(List.of());
        when(analyticsRepository.employeeCountByCountry()).thenReturn(List.of());

        analyticsService.payrollByCountry();
        analyticsService.salaryByDepartment();
        analyticsService.salaryByDesignation();
        analyticsService.salaryDistribution();
        analyticsService.employeeCountByCountry();

        verify(analyticsRepository).payrollByCountry(asOf);
        verify(analyticsRepository).salaryByDepartment(asOf);
        verify(analyticsRepository).salaryByDesignation(asOf);
        verify(analyticsRepository).salaryDistribution(asOf);
        verify(analyticsRepository).employeeCountByCountry();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
