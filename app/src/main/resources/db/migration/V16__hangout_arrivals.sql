-- "I'm here". The time someone said they arrived, kept so the button remembers and anyone arriving late
-- can see who is already there. Moving the date or the spot clears it, since they arrived for the old plan.
ALTER TABLE hangout_service.hangout_participants
    ADD COLUMN arrived_at timestamp(6) with time zone;

-- An hour before the start, everyone going is told it starts soon, once per date like the other reminders.
ALTER TABLE hangout_service.hangout_reminders
    DROP CONSTRAINT hangout_reminders_kind_check;

ALTER TABLE hangout_service.hangout_reminders
    ADD CONSTRAINT hangout_reminders_kind_check CHECK (kind IN (
        'SPOT_BEFORE_START',
        'SPOT_AFTER_START',
        'COMPLETION_DAY_1',
        'COMPLETION_DAY_3',
        'COMPLETION_DAY_7',
        'SOLO_BEFORE_START',
        'STARTING_SOON'
    ));

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
        'HANGOUT_STARTING_SOON',
        'PARTICIPANT_ARRIVED',
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
