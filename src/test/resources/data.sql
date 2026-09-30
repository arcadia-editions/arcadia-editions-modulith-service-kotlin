-- ============================================================================
-- Test data for arcadia-editions-modulith-service-kotlin (PlaceOrderFlow)
-- ============================================================================
-- Generated with the zenwave-test-data skill
-- (arcadia-editions-architecture/skills/generic/zenwave-test-data/SKILL.md)
-- from target/ddl/schema.sql (Hibernate DDL, 2026-09-27), the ZDL lifecycles and the ZFL flow.
-- The JSON fixtures in data/jpa/<Entity>/<k>.json hold the same rows, with the same ids.
--
-- Ids come from sequences with increment 50: row k of each table has id 1 + (k-1)*50.
-- Inventory counters are derived from the reservations (RESERVED -> reserved, CONFIRMED -> sold).
--
-- Scenario chains (all times 2026-09-26, UTC, currency EUR):
-- | chain   | customer | order     | reservations          | payment                     | fulfillment   | notification               |
-- |---------|----------|-----------|-----------------------|-----------------------------|---------------|----------------------------|
-- | ORD-001 | CUST-001 | CREATED   | RES-001/002 RESERVED  | PAY-001 AUTHORIZATION_REQ.  | -             | -                          |
-- | ORD-002 | CUST-002 | CONFIRMED | RES-003 CONFIRMED     | PAY-002 AUTHORIZED          | FUL-002 SCHED | -                          |
-- | ORD-003 | CUST-001 | CONFIRMED | RES-004/005 CONFIRMED | PAY-003 CAPTURED            | FUL-003 READY | NOT-001 CONFIRMATION SENT  |
-- | ORD-004 | CUST-002 | CREATED   | RES-006 RESERVED      | PAY-004 FAILED (attempt 1)  | -             | -                          |
-- | ORD-005 | CUST-003 | CANCELLED | RES-007 RELEASED      | PAY-005 DECLINED            | -             | NOT-002 CANCELLED SENT     |
-- | ORD-006 | CUST-003 | CANCELLED | RES-008 RELEASED*     | PAY-006 VOIDED              | FUL-006 FAILED| NOT-003 CANCELLED PENDING  |
-- | ORD-007 | CUST-004 | (none)**  | -                     | -                           | -             | NOT-004 STOCK_UNAV. FAILED |
-- | ORD-008 | CUST-004 | (none)*** | -                     | PAY-007 FAILED (attempt 3)  | -             | -                          |
--  *  released from CONFIRMED (compensation after fulfillment failure)
--  ** stock unavailable: per the ZFL, no order record is created (M-05 / Q-03 still open)
--  *** payments-only fixture: retryPayment/capturePayment/voidPayment never call orders-checkout,
--      so PAY-007's order does not need a checkout_order row.
--
-- Used by: confirmOrder/cancelOrder (ORD-001, ORD-004), reserveStock unavailable branch (AE-EDN-003, 0 available),
-- retryPayment (PAY-004 attempt 1->2, PAY-007 attempt 3 exhausted), capturePayment/voidPayment (PAY-002),
-- releaseStock (RES-001/002/006, RES-003..005), markReadyToShip (FUL-002), product publish/update
-- (AE-EDN-004 DRAFT), retire (AE-EDN-001/002/003).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Catalog / Products: product (5 rows, ids 1, 51, 101, 151, 201)
-- ---------------------------------------------------------------------------
-- 1. PUBLISHED AE-EDN-001
INSERT INTO product (id, version, sku, name, description, price, currency, edition_size, tracking_mode, launch_at, status)
VALUES (nextval('product_seq'), 0, 'AE-EDN-001', 'Arcadia First Edition', 'Signed limited print of the Arcadia First Edition cover art', 49.99, 'EUR', 100, 'QUANTITY', '2026-09-26T00:00:00Z', 'PUBLISHED');
-- 2. PUBLISHED AE-PRT-002
INSERT INTO product (id, version, sku, name, description, price, currency, edition_size, tracking_mode, launch_at, status)
VALUES (nextval('product_seq'), 0, 'AE-PRT-002', 'Arcadia Map Print', 'Numbered giclee print of the Arcadia hand-drawn map', 25.00, 'EUR', 250, 'QUANTITY', '2026-09-26T00:00:00Z', 'PUBLISHED');
-- 3. PUBLISHED AE-EDN-003
INSERT INTO product (id, version, sku, name, description, price, currency, edition_size, tracking_mode, launch_at, status)
VALUES (nextval('product_seq'), 0, 'AE-EDN-003', 'Arcadia Collector Box', 'Collector box with slipcase and enamel pin (sold out)', 79.00, 'EUR', 50, 'QUANTITY', '2026-09-20T00:00:00Z', 'PUBLISHED');
-- 4. DRAFT AE-EDN-004
INSERT INTO product (id, version, sku, name, description, price, currency, edition_size, tracking_mode, launch_at, status)
VALUES (nextval('product_seq'), 0, 'AE-EDN-004', 'Arcadia Second Edition', 'Upcoming second edition drop', 59.99, 'EUR', 150, 'QUANTITY', '2026-10-15T00:00:00Z', 'DRAFT');
-- 5. RETIRED AE-EDN-000
INSERT INTO product (id, version, sku, name, description, price, currency, edition_size, tracking_mode, launch_at, status)
VALUES (nextval('product_seq'), 0, 'AE-EDN-000', 'Arcadia Preview Zine', 'Preview zine, no longer sold', 9.99, 'EUR', 500, 'QUANTITY', '2026-01-10T00:00:00Z', 'RETIRED');

-- ---------------------------------------------------------------------------
-- Catalog / Inventory: inventory_position (3 rows, ids 1, 51, 101)
-- ---------------------------------------------------------------------------
-- 1. AE-EDN-001
INSERT INTO inventory_position (id, version, sku, available_quantity, reserved_quantity, sold_quantity)
VALUES (nextval('inventory_position_seq'), 0, 'AE-EDN-001', 96, 2, 2);
-- 2. AE-PRT-002
INSERT INTO inventory_position (id, version, sku, available_quantity, reserved_quantity, sold_quantity)
VALUES (nextval('inventory_position_seq'), 0, 'AE-PRT-002', 247, 2, 1);
-- 3. AE-EDN-003
INSERT INTO inventory_position (id, version, sku, available_quantity, reserved_quantity, sold_quantity)
VALUES (nextval('inventory_position_seq'), 0, 'AE-EDN-003', 0, 0, 50);

-- ---------------------------------------------------------------------------
-- Catalog / Inventory: stock_reservation (8 rows, ids 1, 51, 101, 151, 201, 251, 301, 351)
-- ---------------------------------------------------------------------------
-- 1. RESERVED ORD-001
INSERT INTO stock_reservation (id, version, reservation_id, order_id, sku, quantity, status, reserved_at, expires_at, confirmed_at, released_at, release_reason)
VALUES (nextval('stock_reservation_seq'), 0, 'RES-001', 'ORD-001', 'AE-EDN-001', 1, 'RESERVED', '2026-09-26T10:00:00Z', '2026-09-26T10:10:00Z', NULL, NULL, NULL);
-- 2. RESERVED ORD-001
INSERT INTO stock_reservation (id, version, reservation_id, order_id, sku, quantity, status, reserved_at, expires_at, confirmed_at, released_at, release_reason)
VALUES (nextval('stock_reservation_seq'), 0, 'RES-002', 'ORD-001', 'AE-PRT-002', 2, 'RESERVED', '2026-09-26T10:00:00Z', '2026-09-26T10:10:00Z', NULL, NULL, NULL);
-- 3. CONFIRMED ORD-002
INSERT INTO stock_reservation (id, version, reservation_id, order_id, sku, quantity, status, reserved_at, expires_at, confirmed_at, released_at, release_reason)
VALUES (nextval('stock_reservation_seq'), 0, 'RES-003', 'ORD-002', 'AE-EDN-001', 1, 'CONFIRMED', '2026-09-26T09:00:00Z', '2026-09-26T09:10:00Z', '2026-09-26T09:01:00Z', NULL, NULL);
-- 4. CONFIRMED ORD-003
INSERT INTO stock_reservation (id, version, reservation_id, order_id, sku, quantity, status, reserved_at, expires_at, confirmed_at, released_at, release_reason)
VALUES (nextval('stock_reservation_seq'), 0, 'RES-004', 'ORD-003', 'AE-EDN-001', 1, 'CONFIRMED', '2026-09-26T08:00:00Z', '2026-09-26T08:10:00Z', '2026-09-26T08:01:00Z', NULL, NULL);
-- 5. CONFIRMED ORD-003
INSERT INTO stock_reservation (id, version, reservation_id, order_id, sku, quantity, status, reserved_at, expires_at, confirmed_at, released_at, release_reason)
VALUES (nextval('stock_reservation_seq'), 0, 'RES-005', 'ORD-003', 'AE-PRT-002', 1, 'CONFIRMED', '2026-09-26T08:00:00Z', '2026-09-26T08:10:00Z', '2026-09-26T08:01:00Z', NULL, NULL);
-- 6. RESERVED ORD-004
INSERT INTO stock_reservation (id, version, reservation_id, order_id, sku, quantity, status, reserved_at, expires_at, confirmed_at, released_at, release_reason)
VALUES (nextval('stock_reservation_seq'), 0, 'RES-006', 'ORD-004', 'AE-EDN-001', 1, 'RESERVED', '2026-09-26T10:05:00Z', '2026-09-26T10:15:00Z', NULL, NULL, NULL);
-- 7. RELEASED ORD-005
INSERT INTO stock_reservation (id, version, reservation_id, order_id, sku, quantity, status, reserved_at, expires_at, confirmed_at, released_at, release_reason)
VALUES (nextval('stock_reservation_seq'), 0, 'RES-007', 'ORD-005', 'AE-PRT-002', 1, 'RELEASED', '2026-09-26T07:00:00Z', '2026-09-26T07:10:00Z', NULL, '2026-09-26T07:02:00Z', 'PAYMENT_DECLINED');
-- 8. RELEASED ORD-006
INSERT INTO stock_reservation (id, version, reservation_id, order_id, sku, quantity, status, reserved_at, expires_at, confirmed_at, released_at, release_reason)
VALUES (nextval('stock_reservation_seq'), 0, 'RES-008', 'ORD-006', 'AE-EDN-001', 1, 'RELEASED', '2026-09-26T06:00:00Z', '2026-09-26T06:10:00Z', '2026-09-26T06:01:00Z', '2026-09-26T06:30:00Z', 'PAYMENT_VOIDED');

-- ---------------------------------------------------------------------------
-- Orders / Checkout: checkout_order (6 rows, ids 1, 51, 101, 151, 201, 251)
-- ---------------------------------------------------------------------------
-- 1. CREATED ORD-001
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-001', 'CREATED', 'CUST-001', 'ada@example.com', 'pm_card_visa', '[{"sku": "AE-EDN-001", "quantity": 1, "unitPrice": 49.99}, {"sku": "AE-PRT-002", "quantity": 2, "unitPrice": 25.0}]'::jsonb, '{"name": "Ada Lovelace", "line1": "12 Marylebone Rd", "line2": null, "city": "London", "region": null, "postalCode": "NW1 5LR", "country": "GB"}'::jsonb, 99.99, 'EUR', '2026-09-26T10:00:00Z', NULL, NULL, NULL);
-- 2. CONFIRMED ORD-002
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-002', 'CONFIRMED', 'CUST-002', 'carmen@example.com', 'pm_card_visa', '[{"sku": "AE-EDN-001", "quantity": 1, "unitPrice": 49.99}]'::jsonb, '{"name": "Carmen Laforet", "line1": "Calle de Alcala 45", "line2": "3B", "city": "Madrid", "region": "Madrid", "postalCode": "28014", "country": "ES"}'::jsonb, 49.99, 'EUR', '2026-09-26T09:00:00Z', '2026-09-26T09:01:00Z', NULL, NULL);
-- 3. CONFIRMED ORD-003
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-003', 'CONFIRMED', 'CUST-001', 'ada@example.com', 'pm_card_visa', '[{"sku": "AE-EDN-001", "quantity": 1, "unitPrice": 49.99}, {"sku": "AE-PRT-002", "quantity": 1, "unitPrice": 25.0}]'::jsonb, '{"name": "Ada Lovelace", "line1": "12 Marylebone Rd", "line2": null, "city": "London", "region": null, "postalCode": "NW1 5LR", "country": "GB"}'::jsonb, 74.99, 'EUR', '2026-09-26T08:00:00Z', '2026-09-26T08:01:00Z', NULL, NULL);
-- 4. CREATED ORD-004
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-004', 'CREATED', 'CUST-002', 'carmen@example.com', 'pm_card_mastercard', '[{"sku": "AE-EDN-001", "quantity": 1, "unitPrice": 49.99}]'::jsonb, '{"name": "Carmen Laforet", "line1": "Calle de Alcala 45", "line2": "3B", "city": "Madrid", "region": "Madrid", "postalCode": "28014", "country": "ES"}'::jsonb, 49.99, 'EUR', '2026-09-26T10:05:00Z', NULL, NULL, NULL);
-- 5. CANCELLED ORD-005
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-005', 'CANCELLED', 'CUST-003', 'italo@example.com', 'pm_card_visa', '[{"sku": "AE-PRT-002", "quantity": 1, "unitPrice": 25.0}]'::jsonb, '{"name": "Italo Calvino", "line1": "Via Po 18", "line2": null, "city": "Torino", "region": "TO", "postalCode": "10123", "country": "IT"}'::jsonb, 25.00, 'EUR', '2026-09-26T07:00:00Z', NULL, '2026-09-26T07:02:00Z', 'PAYMENT_DECLINED');
-- 6. CANCELLED ORD-006
INSERT INTO checkout_order (id, version, order_id, status, customer_id, customer_email, payment_method_id, items, shipping_address, total_amount, currency, created_at, confirmed_at, cancelled_at, cancellation_reason)
VALUES (nextval('checkout_order_seq'), 0, 'ORD-006', 'CANCELLED', 'CUST-003', 'italo@example.com', 'pm_card_visa', '[{"sku": "AE-EDN-001", "quantity": 1, "unitPrice": 49.99}]'::jsonb, '{"name": "Italo Calvino", "line1": "Via Po 18", "line2": null, "city": "Torino", "region": "TO", "postalCode": "10123", "country": "IT"}'::jsonb, 49.99, 'EUR', '2026-09-26T06:00:00Z', '2026-09-26T06:01:00Z', '2026-09-26T06:31:00Z', 'PAYMENT_VOIDED');

-- ---------------------------------------------------------------------------
-- Payments / Processing: payment (7 rows, ids 1, 51, 101, 151, 201, 251, 301)
-- ---------------------------------------------------------------------------
-- 1. AUTHORIZATION_REQUESTED ORD-001
INSERT INTO payment (id, version, payment_id, order_id, customer_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'PAY-001', 'ORD-001', 'CUST-001', 'ada@example.com', 99.99, 'EUR', 'AUTHORIZATION_REQUESTED', NULL, 1, NULL, NULL, NULL, NULL, NULL, NULL);
-- 2. AUTHORIZED ORD-002
INSERT INTO payment (id, version, payment_id, order_id, customer_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'PAY-002', 'ORD-002', 'CUST-002', 'carmen@example.com', 49.99, 'EUR', 'AUTHORIZED', 'psp_txn_002', 1, '2026-09-26T09:01:00Z', NULL, NULL, NULL, NULL, NULL);
-- 3. CAPTURED ORD-003
INSERT INTO payment (id, version, payment_id, order_id, customer_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'PAY-003', 'ORD-003', 'CUST-001', 'ada@example.com', 74.99, 'EUR', 'CAPTURED', 'psp_txn_003', 1, '2026-09-26T08:01:00Z', NULL, NULL, '2026-09-26T08:30:00Z', NULL, NULL);
-- 4. FAILED ORD-004
INSERT INTO payment (id, version, payment_id, order_id, customer_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'PAY-004', 'ORD-004', 'CUST-002', 'carmen@example.com', 49.99, 'EUR', 'FAILED', NULL, 1, NULL, NULL, '2026-09-26T10:06:00Z', NULL, NULL, 'PROVIDER_TIMEOUT');
-- 5. DECLINED ORD-005
INSERT INTO payment (id, version, payment_id, order_id, customer_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'PAY-005', 'ORD-005', 'CUST-003', 'italo@example.com', 25.00, 'EUR', 'DECLINED', 'psp_txn_005', 1, NULL, '2026-09-26T07:01:00Z', NULL, NULL, NULL, 'CARD_DECLINED');
-- 6. VOIDED ORD-006
INSERT INTO payment (id, version, payment_id, order_id, customer_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'PAY-006', 'ORD-006', 'CUST-003', 'italo@example.com', 49.99, 'EUR', 'VOIDED', 'psp_txn_006', 1, '2026-09-26T06:01:00Z', NULL, NULL, NULL, '2026-09-26T06:25:00Z', 'FULFILLMENT_FAILED');
-- 7. FAILED ORD-008, 3rd attempt already spent (G-PP-03/Q-05: retryPayment -> RETRY_EXHAUSTED)
INSERT INTO payment (id, version, payment_id, order_id, customer_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'PAY-007', 'ORD-008', 'CUST-004', 'jorge@example.com', 39.99, 'EUR', 'FAILED', NULL, 3, NULL, NULL, '2026-09-26T11:06:00Z', NULL, NULL, 'PROVIDER_TIMEOUT');

-- ---------------------------------------------------------------------------
-- Fulfillment / Shipping: fulfillment (3 rows, ids 1, 51, 101)
-- ---------------------------------------------------------------------------
-- 1. SCHEDULED ORD-002
INSERT INTO fulfillment (id, version, fulfillment_id, order_id, customer_id, status, shipping_address, items, carrier_code, tracking_number, scheduled_at, ready_at, failed_at, failure_reason)
VALUES (nextval('fulfillment_seq'), 0, 'FUL-002', 'ORD-002', 'CUST-002', 'SCHEDULED', '{"name": "Carmen Laforet", "line1": "Calle de Alcala 45", "line2": "3B", "city": "Madrid", "region": "Madrid", "postalCode": "28014", "country": "ES"}'::jsonb, '[{"sku": "AE-EDN-001", "quantity": 1}]'::jsonb, 'DHL', NULL, '2026-09-26T09:05:00Z', NULL, NULL, NULL);
-- 2. READY_TO_SHIP ORD-003
INSERT INTO fulfillment (id, version, fulfillment_id, order_id, customer_id, status, shipping_address, items, carrier_code, tracking_number, scheduled_at, ready_at, failed_at, failure_reason)
VALUES (nextval('fulfillment_seq'), 0, 'FUL-003', 'ORD-003', 'CUST-001', 'READY_TO_SHIP', '{"name": "Ada Lovelace", "line1": "12 Marylebone Rd", "line2": null, "city": "London", "region": null, "postalCode": "NW1 5LR", "country": "GB"}'::jsonb, '[{"sku": "AE-EDN-001", "quantity": 1}, {"sku": "AE-PRT-002", "quantity": 1}]'::jsonb, 'DHL', 'JD014600006281', '2026-09-26T08:05:00Z', '2026-09-26T08:40:00Z', NULL, NULL);
-- 3. FAILED ORD-006
INSERT INTO fulfillment (id, version, fulfillment_id, order_id, customer_id, status, shipping_address, items, carrier_code, tracking_number, scheduled_at, ready_at, failed_at, failure_reason)
VALUES (nextval('fulfillment_seq'), 0, 'FUL-006', 'ORD-006', 'CUST-003', 'FAILED', '{"name": "Italo Calvino", "line1": "Via Po 18", "line2": null, "city": "Torino", "region": "TO", "postalCode": "10123", "country": "IT"}'::jsonb, '[{"sku": "AE-EDN-001", "quantity": 1}]'::jsonb, 'DHL', NULL, '2026-09-26T06:05:00Z', NULL, '2026-09-26T06:20:00Z', 'Carrier pickup unavailable');

-- ---------------------------------------------------------------------------
-- Notifications / Consumer: notification (4 rows, ids 1, 51, 101, 151)
-- ---------------------------------------------------------------------------
-- 1. SENT ORD-003
INSERT INTO notification (id, version, notification_id, customer_id, order_id, skus, type, channel, status, subject, body, recipient, created_at, sent_at, failed_at, failure_reason)
VALUES (nextval('notification_seq'), 0, 'NOT-001', 'CUST-001', 'ORD-003', '{}', 'ORDER_CONFIRMATION', 'EMAIL', 'SENT', 'Your Arcadia Editions order ORD-003 is confirmed', 'Thank you! Your order ORD-003 has been paid and is ready to ship.', 'ada@example.com', '2026-09-26T08:30:00Z', '2026-09-26T08:31:00Z', NULL, NULL);
-- 2. SENT ORD-005
INSERT INTO notification (id, version, notification_id, customer_id, order_id, skus, type, channel, status, subject, body, recipient, created_at, sent_at, failed_at, failure_reason)
VALUES (nextval('notification_seq'), 0, 'NOT-002', 'CUST-003', 'ORD-005', '{}', 'ORDER_CANCELLED', 'EMAIL', 'SENT', 'Your Arcadia Editions order ORD-005 was cancelled', 'Your payment was declined, so order ORD-005 was cancelled. No charge was made.', 'italo@example.com', '2026-09-26T07:02:00Z', '2026-09-26T07:03:00Z', NULL, NULL);
-- 3. PENDING ORD-006
INSERT INTO notification (id, version, notification_id, customer_id, order_id, skus, type, channel, status, subject, body, recipient, created_at, sent_at, failed_at, failure_reason)
VALUES (nextval('notification_seq'), 0, 'NOT-003', 'CUST-003', 'ORD-006', '{}', 'ORDER_CANCELLED', 'EMAIL', 'PENDING', 'Your Arcadia Editions order ORD-006 was cancelled', 'We could not arrange shipping for order ORD-006. The payment hold was released.', 'italo@example.com', '2026-09-26T06:31:00Z', NULL, NULL, NULL);
-- 4. FAILED ORD-007
INSERT INTO notification (id, version, notification_id, customer_id, order_id, skus, type, channel, status, subject, body, recipient, created_at, sent_at, failed_at, failure_reason)
VALUES (nextval('notification_seq'), 0, 'NOT-004', 'CUST-004', 'ORD-007', ARRAY['AE-EDN-003']::varchar[], 'STOCK_UNAVAILABLE', 'EMAIL', 'FAILED', 'Arcadia Collector Box is sold out', 'Sorry, AE-EDN-003 sold out before we could reserve it for you.', 'jorge@example.com', '2026-09-26T10:20:00Z', NULL, '2026-09-26T10:21:00Z', 'SMTP 550 mailbox unavailable');
