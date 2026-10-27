-- Channel 1 (POST /api/service/process) is deny-by-default on engine-spring 0.3.49 and
-- reads its grants from service_process_grant. DEMO is a callee (service-process is on in
-- the compose stack) and the platform's Integration Trace (TRAC) reads this module's
-- event ledgers through three workflows; without these rows every TRAC call gets a 403:
--   platform-service -> event.outboxLog   R   (cmd_search, cmd_view)
--   platform-service -> event.inboxLog    R
--   platform-service -> event.errorLog    R
--
-- cpy_cd is '*' so the grant covers every company; an exact-company row still overrides it.
-- No row for test.demo: it is a TEMP echo workflow with no configured caller.
--
-- enabled/created_at/updated_at take their column defaults. Idempotent and portable (MERGE).

MERGE INTO demo.service_process_grant AS t
USING (VALUES
    ('*', 'platform-service', 'event.outboxLog', 'R'),
    ('*', 'platform-service', 'event.inboxLog',  'R'),
    ('*', 'platform-service', 'event.errorLog',  'R')) AS s (cpy_cd, service, workflow, right_code)
     ON t.cpy_cd = s.cpy_cd
       AND t.service = s.service
       AND t.workflow = s.workflow
   WHEN NOT MATCHED THEN
INSERT (cpy_cd, service, workflow, right_code)
VALUES (s.cpy_cd, s.service, s.workflow, s.right_code);
