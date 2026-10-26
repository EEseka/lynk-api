-- A reminder to the host of a hangout nobody else has joined, sent when the start is less than a day
-- away, asking them to invite whoever is coming.
--
-- A hangout only its host is going to gets no reminders to pick a spot and none to mark it complete,
-- since each of those talks to a group that isn't there. This one push replaces them. It goes once per
-- date: moving the date sends it again for the new one, like the spot reminders.
--
-- Its start still pushes, as that is the moment to head out, but as SOLO_STARTED: friends often come
-- without ever answering in the app, so the host is asked to invite whoever is with them.

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
        'SPOT_REMINDER_BEFORE_START',
        'SPOT_REMINDER_AFTER_START',
        'SOLO_REMINDER',
        'SOLO_STARTED',
        'PAYMENT_DEADLINE_RESOLVED',
        'PAYMENT_DEADLINE_NEEDS_DECISION',
        'PAYMENT_DEADLINE_CHANGED',
        'PAYOUT_SUCCEEDED',
        'PAYOUT_FAILED',
        'PAYOUT_NOTHING_TO_SEND',
        'PAYMENT_RECEIVED',
        'REFUND_ISSUED'
    ));
