-- Schema baseline. Employee and salary tables are added in later migrations.
CREATE TABLE paylens_schema_meta (
    id INTEGER PRIMARY KEY,
    note VARCHAR(255) NOT NULL
);

INSERT INTO paylens_schema_meta (id, note) VALUES (1, 'baseline');
