CREATE TABLE employee (
    id         BIGINT PRIMARY KEY,
    name       VARCHAR(100),
    manager_id BIGINT
);

INSERT INTO employee (id, name, manager_id) VALUES
    (1, 'CEO',     NULL),
    (2, 'Manager', 1),
    (3, 'Lead',    2),
    (5, 'Staff',   3);