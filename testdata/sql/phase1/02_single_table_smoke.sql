-- Phase 1 acceptance: single table only smoke
CREATE TABLE events (id INT, label TEXT);
INSERT INTO events VALUES (10, 'start');
INSERT INTO events VALUES (11, 'stop');
SELECT * FROM events;
