-- A professional ZIP quote entered payment before the production status was
-- wired into the callback.  Repair those already-paid rows without changing
-- any applied migration.
UPDATE consumer_professional_submission
SET status='processing'
WHERE status='approved'
  AND sample_payment_status='paid';
