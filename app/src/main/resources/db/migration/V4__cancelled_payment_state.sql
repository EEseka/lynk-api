-- A CANCELLED payment state, so a cancelled hangout stops saying its money is still being collected.
--
-- Cancelling used to change the hangout's status alone. The payment state stayed wherever it was,
-- usually COLLECTING, and nothing moved it on afterward because every sweep skips cancelled
-- hangouts. HangoutAccountDeletionGuard reads that stale state as money still to settle, so a host
-- who cancelled a paid hangout could never delete their account.
--
-- The check constraint lists every allowed state, so it has to be rebuilt to accept the new one.
ALTER TABLE hangout_service.hangouts
    DROP CONSTRAINT hangouts_payment_state_check;

ALTER TABLE hangout_service.hangouts
    ADD CONSTRAINT hangouts_payment_state_check CHECK (payment_state IN (
        'COLLECTING',
        'AWAITING_HOST_DECISION',
        'READY_FOR_PAYOUT',
        'PAYING_OUT',
        'PAID_OUT',
        'PAYOUT_FAILED',
        'CANCELLED'
    ));
