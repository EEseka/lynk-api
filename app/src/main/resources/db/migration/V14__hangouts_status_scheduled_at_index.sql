-- Indexes the question every hangout sweep asks: which hangouts in these states are due by now.
--
-- Starting hangouts, the spot and solo reminders, and the completion nudges all filter on status
-- and a range of scheduled_at, every five or fifteen minutes. Finished hangouts stay in the table,
-- so without this each of those runs reads all of them. Status leads because it is matched
-- exactly and scheduled_at is the range.
CREATE INDEX idx_hangouts_status_scheduled_at
    ON hangout_service.hangouts (status, scheduled_at);
