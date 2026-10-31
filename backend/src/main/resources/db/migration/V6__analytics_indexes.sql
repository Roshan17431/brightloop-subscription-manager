-- Phase 4: Financial Analytics & Customer Portal composite indexes
CREATE INDEX IF NOT EXISTS idx_subscriptions_analytics
  ON subscriptions(state, plan_id, customer_id, start_date, expiry_date, next_billing_date);

CREATE INDEX IF NOT EXISTS idx_subscription_lines_sub_prod
  ON subscription_lines(subscription_id, product_id);

CREATE INDEX IF NOT EXISTS idx_invoices_analytics_issue
  ON invoices(state, issue_date, customer_id, plan_id, billing_period);

CREATE INDEX IF NOT EXISTS idx_invoices_analytics_overdue
  ON invoices(due_date, customer_id, plan_id)
  WHERE state = 'CONFIRMED' AND amount_paid < total;

CREATE INDEX IF NOT EXISTS idx_payments_analytics
  ON payments(status, payment_date, method, customer_id);
