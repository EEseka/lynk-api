-- Rebuilds the inbox index newest first, to match every other composite index on a paged list.
--
-- The inbox reads a person's notifications newest first, and Postgres can walk an ascending index
-- backwards just as fast, so this changes nothing about speed. It only brings the declaration in
-- line with saved spots and hangout photos, which were written with created_at DESC.
DROP INDEX notification_service.idx_notifications_user_id_created_at;

CREATE INDEX idx_notifications_user_id_created_at
    ON notification_service.notifications (user_id, created_at DESC);
