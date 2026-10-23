-- Reminders to the host of a hangout that is still voting as its date comes, asking them to pick a spot.
--
-- Only the scheduled sweep moves a hangout to ONGOING, and it only moves hangouts with a spot, so a
-- hangout still voting when its date passes stays voting until the host acts. Nothing flips it on its
-- own on purpose, since a late pick is a real choice. The host gets two pushes instead: one when the
-- start is less than a day away, and one when the start has passed. Then nothing more.
--
-- spot_reminders_sent counts the pushes that went out, so each step goes out once. Moving the date
-- sets it back to zero, so the new date gets its own reminders.
ALTER TABLE hangout_service.hangouts
    ADD COLUMN spot_reminders_sent integer NOT NULL DEFAULT 0;

-- The check constraint lists every allowed type, so it has to be rebuilt to accept the new ones.
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
        'SPOT_REMINDER_BEFORE_START',
        'SPOT_REMINDER_AFTER_START',
        'PAYMENT_DEADLINE_RESOLVED',
        'PAYMENT_DEADLINE_NEEDS_DECISION',
        'PAYMENT_DEADLINE_CHANGED',
        'PAYOUT_SUCCEEDED',
        'PAYOUT_FAILED',
        'PAYOUT_NOTHING_TO_SEND',
        'PAYMENT_RECEIVED',
        'REFUND_ISSUED'
    ));
