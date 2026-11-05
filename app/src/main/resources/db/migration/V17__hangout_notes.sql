-- Short notes people going leave for each other around the start, like "running late" or "we're at the back".
-- Posting opens with I'm Here, an hour before the start, and closes when the hangout ends. Notes stay readable
-- after that, and the author or the host can still delete them.
--
-- ON DELETE CASCADE: unlike photos, notes own no files, so deleting a hangout can take its notes with it.
-- HangoutEntity doesn't map its notes, so Postgres removes them. That happens in the nightly sweep of solo
-- hangouts, which can hold notes: a guest can post one and then leave.
CREATE TABLE hangout_service.hangout_notes (
    id uuid NOT NULL,
    hangout_id uuid NOT NULL,
    author_id uuid NOT NULL,
    content character varying(140) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone,
    CONSTRAINT hangout_notes_pkey PRIMARY KEY (id),
    CONSTRAINT fk_hangout_notes_hangout FOREIGN KEY (hangout_id)
        REFERENCES hangout_service.hangouts (id) ON DELETE CASCADE,
    CONSTRAINT fk_hangout_notes_author FOREIGN KEY (author_id)
        REFERENCES hangout_service.hangout_users (user_id)
);

-- The notes list starts from the hangout, newest first.
CREATE INDEX idx_hangout_notes_hangout_id_created_at
    ON hangout_service.hangout_notes (hangout_id, created_at DESC);

-- Deleting an account removes every note that person wrote.
CREATE INDEX idx_hangout_notes_author_id
    ON hangout_service.hangout_notes (author_id);

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
        'HANGOUT_STARTING_SOON',
        'PARTICIPANT_ARRIVED',
        'NOTE_ADDED',
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
