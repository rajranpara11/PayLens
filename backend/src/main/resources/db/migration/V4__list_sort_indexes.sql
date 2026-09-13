-- Support default employee list sort (lastName ASC) and common status+name browsing.
CREATE INDEX idx_employee_last_name ON employee (last_name);
CREATE INDEX idx_employee_status_last_name ON employee (employment_status, last_name);
