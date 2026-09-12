-- Analytics-friendly indexes. Current-salary lookups already benefit from
-- uq_salary_employee_effective_from (employee_id, effective_from).

CREATE INDEX idx_salary_currency ON salary (currency);
CREATE INDEX idx_salary_effective_from ON salary (effective_from);
CREATE INDEX idx_employee_status_country ON employee (employment_status, country);
