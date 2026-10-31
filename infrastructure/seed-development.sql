-- Optional sample catalog only. Run after Flyway on a disposable development database.
-- No users, credentials, customer records, subscriptions, invoices or payments are seeded.
BEGIN;
INSERT INTO products (name, type, sales_price, cost_price, notes, status, created_at, updated_at)
SELECT 'Business Workspace', 'SOFTWARE', 1000.00, 400.00, 'Optional development sample catalog', 'ACTIVE', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Business Workspace' AND notes = 'Optional development sample catalog');
INSERT INTO product_variants (product_id, name, price_adjustment)
SELECT p.id, 'Odoo brand', 560.00 FROM products p
WHERE p.name = 'Business Workspace' AND p.notes = 'Optional development sample catalog'
AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.name = 'Odoo brand');
INSERT INTO plans (name, description, billing_period, price, minimum_quantity, available_from, available_until,
 automatic_closing, early_closing_allowed, pausing_allowed, renewal_allowed, status, created_at, updated_at)
SELECT 'Workspace Monthly', 'Optional development sample plan', 'MONTHLY', 1000.00, 1, current_date, current_date + interval '1 year',
 false, true, true, true, 'ACTIVE', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM plans WHERE name = 'Workspace Monthly' AND description = 'Optional development sample plan');
INSERT INTO plan_products (plan_id, product_id)
SELECT pl.id, p.id FROM plans pl CROSS JOIN products p
WHERE pl.name = 'Workspace Monthly' AND pl.description = 'Optional development sample plan'
AND p.name = 'Business Workspace' AND p.notes = 'Optional development sample catalog'
AND NOT EXISTS (SELECT 1 FROM plan_products pp WHERE pp.plan_id = pl.id AND pp.product_id = p.id);
COMMIT;
