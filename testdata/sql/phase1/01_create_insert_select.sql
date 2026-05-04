-- Phase 1 acceptance: basic create/insert/select
CREATE TABLE users (id INT, name TEXT);
INSERT INTO users VALUES (1, 'alice');
INSERT INTO users VALUES (2, 'bob');
SELECT * FROM users;
