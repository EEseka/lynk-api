-- A version number on every hangout, so two saves of the same hangout cannot silently overwrite
-- each other.
--
-- A hangout row is saved by the host, by every guest who joins or leaves, by the Paystack webhook
-- and by the scheduled sweeps. Each of those loads the whole row, changes its copy and writes the
-- whole row back, so without this the later save quietly undoes the earlier one - a guest joining
-- at the moment the host turns payments on could write payments back off.
--
-- Hibernate now saves with "WHERE version = <the version I loaded>" and bumps it by one. If someone
-- else saved in between, no row matches and the save fails instead of overwriting.
--
-- Existing hangouts start at 0, which is all a version needs: it only has to change on every save.
ALTER TABLE hangout_service.hangouts
    ADD COLUMN version bigint NOT NULL DEFAULT 0;
