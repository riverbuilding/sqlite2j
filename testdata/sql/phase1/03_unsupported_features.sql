-- Phase 1 acceptance: unsupported features should fail deterministically
CREATE TABLE t (id INT, v TEXT);
INSERT INTO t VALUES (1, 'x');
SELECT * FROM t WHERE id = 1;
SELECT * FROM t ORDER BY id;
UPDATE t SET v = 'y' WHERE id = 1;
DELETE FROM t WHERE id = 1;
BEGIN;
COMMIT;
CREATE INDEX idx_t_id ON t(id);
