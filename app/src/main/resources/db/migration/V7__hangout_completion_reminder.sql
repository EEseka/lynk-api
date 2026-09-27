-- Reminders to the host of a hangout that is still going after it started, asking them to mark it
-- complete so its album opens.
--
-- Only the host can finish a hangout, and there is no end time or automatic cutoff on purpose, since
-- people keep extending a good hangout. So a host who forgets leaves the album locked; these are the
-- nudges. We can't know when a hangout ends, so the host gets one push each at 1, 3 and 7 days after
-- the start, which covers a night out, a weekend and a week-long trip, and then nothing more.
--
-- completion_reminders_sent counts the pushes that went out, so each step goes out once. Hangouts
-- already past the first step today are marked as fully reminded, so the first run does not remind
-- hosts about hangouts that finished long ago.
ALTER TABLE hangout_service.hangouts
    ADD COLUMN completion_reminders_sent integer NOT NULL DEFAULT 0;

UPDATE hangout_service.hangouts
SET completion_reminders_sent = 3
WHERE status = 'ONGOING'
AND scheduled_at < now() - interval '24 hours';

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
        'PAYMENT_DEADLINE_RESOLVED',
        'PAYMENT_DEADLINE_NEEDS_DECISION',
        'PAYMENT_DEADLINE_CHANGED',
        'PAYOUT_SUCCEEDED',
        'PAYOUT_FAILED',
        'PAYOUT_NOTHING_TO_SEND',
        'PAYMENT_RECEIVED',
        'REFUND_ISSUED'
    ));
