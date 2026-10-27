-- UNDO for V20261027120000. Run by hand from the repo, never by the app on boot
-- (see platform/docs/migrations.md).
--
-- Removes the platform-service read grants, so the platform's Integration Trace gets a
-- 403 from DEMO again while Channel 1 enforces grants.

DELETE FROM demo.service_process_grant
 WHERE cpy_cd = '*'
   AND service = 'platform-service'
   AND workflow IN ('event.outboxLog', 'event.inboxLog', 'event.errorLog');
