-- A participant row means nothing without its hangout, so deleting a hangout now takes its participants
-- with it, like its notes and reminders. The nightly sweep of solo hangouts deletes them with one query,
-- which Hibernate's own cascade never sees.
--
-- Photos keep no cascade on purpose: their files live in storage, and a refused delete is what makes
-- the code remove the files first.
ALTER TABLE hangout_service.hangout_participants
    DROP CONSTRAINT fknf56te4r3qy6ebopb7ehvuvd7;

ALTER TABLE hangout_service.hangout_participants
    ADD CONSTRAINT fk_hangout_participants_hangout FOREIGN KEY (hangout_id)
        REFERENCES hangout_service.hangouts (id) ON DELETE CASCADE;
