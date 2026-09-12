-- Normalized employee + salary schema.
-- Salary lives in its own table so an employee can have many dated rows (history).

CREATE TABLE department (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_department_code UNIQUE (code),
    CONSTRAINT ck_department_code_not_blank CHECK (TRIM(code) <> ''),
    CONSTRAINT ck_department_name_not_blank CHECK (TRIM(name) <> '')
);

CREATE TABLE employee (
    id UUID PRIMARY KEY,
    employee_code VARCHAR(32) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    country CHAR(2) NOT NULL,
    department_id UUID NOT NULL,
    designation VARCHAR(120) NOT NULL,
    employment_status VARCHAR(20) NOT NULL,
    joining_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_employee_code UNIQUE (employee_code),
    CONSTRAINT uq_employee_email UNIQUE (email),
    CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT ck_employee_code_not_blank CHECK (TRIM(employee_code) <> ''),
    CONSTRAINT ck_employee_first_name_not_blank CHECK (TRIM(first_name) <> ''),
    CONSTRAINT ck_employee_last_name_not_blank CHECK (TRIM(last_name) <> ''),
    CONSTRAINT ck_employee_email_format CHECK (POSITION('@' IN email) > 1),
    CONSTRAINT ck_employee_country CHECK (LENGTH(TRIM(country)) = 2 AND country = UPPER(country)),
    CONSTRAINT ck_employee_designation_not_blank CHECK (TRIM(designation) <> ''),
    CONSTRAINT ck_employee_status CHECK (employment_status IN ('ACTIVE', 'ON_LEAVE', 'TERMINATED'))
);

CREATE TABLE salary (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL,
    annual_salary NUMERIC(15, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    effective_from DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_salary_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE RESTRICT,
    CONSTRAINT uq_salary_employee_effective_from UNIQUE (employee_id, effective_from),
    CONSTRAINT ck_salary_annual_positive CHECK (annual_salary > 0),
    CONSTRAINT ck_salary_currency CHECK (LENGTH(TRIM(currency)) = 3 AND currency = UPPER(currency))
);

CREATE INDEX idx_employee_country ON employee (country);
CREATE INDEX idx_employee_department_id ON employee (department_id);
CREATE INDEX idx_employee_designation ON employee (designation);
CREATE INDEX idx_employee_employment_status ON employee (employment_status);
