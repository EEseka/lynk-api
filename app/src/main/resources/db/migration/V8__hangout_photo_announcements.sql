-- Tells the other people who went when someone adds photos to an album.
--
-- Each photo is confirmed on its own and the server never learns when a batch ends, so a push per
-- confirmation would send ten pushes for ten photos. Instead a job every few minutes finds finished
-- photos nobody has been told about, groups them by hangout and uploader, sends one push per group
-- ("Ada added 4 photos") and stamps announced_at, so each photo is announced once.
--
-- Photos already in albums today count as announced, so the first run does not push old albums.
ALTER TABLE hangout_service.hangout_photos
    ADD COLUMN announced_at timestamp(6) with time zone;

UPDATE hangout_service.hangout_photos
SET announced_at = now()
WHERE status = 'READY';

-- The job only ever looks for finished photos not yet announced, so an empty run costs one lookup.
CREATE INDEX idx_hangout_photos_unannounced
    ON hangout_service.hangout_photos (hangout_id)
    WHERE status = 'READY' AND announced_at IS NULL;

-- The check constraint lists every allowed type, so it has to be rebuilt to accept the new one.
ALTER TABLE notification_service.notifications
    DROP CONSTRAINT notifications_type_check;

ALTER TABLE notification_service.notifications
    ADD CONSTRAINT notifications_type_check CHECK (type IN (
        'PARTICIPANT_INVITED',
        'INVITE_CANCELLED',
        'REMOVED_FOR_NON_PAYMENT',
        'PARTICIPANT_LEFT',
        'DETAILS_EDITED',
        'SCHEDULE_CHANGED',
        'SPOT_CHOSEN',
        'VOTING_REOPENED',
        'PAYMENTS_ENABLED',
        'HANGOUT_STARTED',
        'HANGOUT_COMPLETED',
        'HANGOUT_COMPLETION_REMINDER',
        'HANGOUT_CANCELLED',
        'PHOTOS_ADDED',
        'PAYMENT_DEADLINE_RESOLVED',
        'PAYMENT_DEADLINE_NEEDS_DECISION',
        'PAYMENT_DEADLINE_CHANGED',
        'PAYOUT_SUCCEEDED',
        'PAYOUT_FAILED',
        'PAYOUT_NOTHING_TO_SEND',
        'PAYMENT_RECEIVED',
        'REFUND_ISSUED'
    ));
