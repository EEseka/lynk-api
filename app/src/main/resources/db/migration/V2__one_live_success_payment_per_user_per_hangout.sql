-- At most one live, unrefunded successful payment per person per hangout.
--
-- Partial rather than a plain UNIQUE (hangout_id, user_id): a person legitimately owns several
-- payment rows for one hangout - a PENDING that turned FAILED, then a retry. Only a SUCCESS that
-- has not been sent back is unique.
--
-- refund_status is part of the predicate because a refund never changes status: RefundService
-- writes refund_status alone, so a refunded row stays SUCCESS forever. Without the second clause,
-- a person who paid, left, was refunded and was invited back could not pay again - and the
-- failure would land after Paystack had already taken their money.
--
-- (SUCCESS, NONE) is the same pair PayoutService and PaymentReconciliationService already use to
-- mean "money we are currently holding".
CREATE UNIQUE INDEX uq_payments_one_live_success_per_user_per_hangout
    ON payment_service.payments (hangout_id, user_id)
    WHERE status = 'SUCCESS' AND refund_status = 'NONE';
