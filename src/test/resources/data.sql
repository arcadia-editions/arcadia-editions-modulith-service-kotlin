-- ============================================================================
-- SQL test data for Arcadia Editions Modulith (Kotlin)
-- ============================================================================
-- Inserts exactly one sample record per entity table.
-- IDs are allocated from Hibernate sequences (increment by 50, first value 1)
-- so repository integration tests can look up id = 1.
-- Logical identifiers (sku, order_id, customer_id) are shared across modules.
-- ============================================================================

-- ============================================================================
-- Catalog / Products
-- ============================================================================

-- Published limited edition used as the catalog sample
INSERT INTO product (
    id,
    version,
    sku,
    name,
    description,
    price,
    currency,
    edition_size,
    tracking_mode,
    launch_at,
    status
)
VALUES (
    nextval('product_seq'),
    0,
    'AE-EDN-001',
    'Arcadia First Edition',
    'Signed limited print of the Arcadia First Edition cover art',
    49.99,
    'EUR',
    100,
    'QUANTITY',
    CURRENT_TIMESTAMP,
    'PUBLISHED'
);

-- ============================================================================
-- Catalog / Inventory
-- ============================================================================

-- On-hand stock for AE-EDN-001
INSERT INTO inventory_position (
    id,
    version,
    sku,
    available_quantity,
    reserved_quantity,
    sold_quantity
)
VALUES (
    nextval('inventory_position_seq'),
    0,
    'AE-EDN-001',
    80,
    5,
    15
);

-- Active reservation against checkout order ORD-001
INSERT INTO stock_reservation (
    id,
    version,
    reservation_id,
    order_id,
    sku,
    quantity,
    status,
    reserved_at,
    expires_at,
    released_at,
    release_reason
)
VALUES (
    nextval('stock_reservation_seq'),
    0,
    'RES-001',
    'ORD-001',
    'AE-EDN-001',
    1,
    'RESERVED',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '15 minutes',
    NULL,
    NULL
);

-- ============================================================================
-- Orders / Checkout
-- ============================================================================

-- Confirmed checkout order for customer CUST-001
INSERT INTO checkout_order (
    id,
    version,
    order_id,
    status,
    items,
    created_at,
    confirmed_at,
    cancelled_at
)
VALUES (
    nextval('checkout_order_seq'),
    0,
    'ORD-001',
    'CONFIRMED',
    ARRAY['AE-EDN-001']::varchar[],
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL
);

-- ============================================================================
-- Payments / Processing
-- ============================================================================

-- Authorized payment for ORD-001
INSERT INTO payment (
    id,
    version,
    payment_id,
    order_id,
    customer_id,
    amount,
    currency,
    status,
    provider_reference,
    attempt_count,
    authorized_at,
    declined_at,
    failed_at,
    captured_at,
    voided_at,
    failure_reason
)
VALUES (
    nextval('payment_seq'),
    0,
    'PAY-001',
    'ORD-001',
    'CUST-001',
    49.99,
    'EUR',
    'AUTHORIZED',
    'psp_txn_001',
    1,
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
);

-- ============================================================================
-- Fulfillment / Shipping
-- ============================================================================

-- Scheduled shipment for ORD-001
INSERT INTO fulfillment (
    id,
    version,
    fulfillment_id,
    order_id,
    customer_id,
    status,
    carrier_code,
    tracking_number,
    scheduled_at,
    ready_at,
    failed_at,
    failure_reason
)
VALUES (
    nextval('fulfillment_seq'),
    0,
    'FUL-001',
    'ORD-001',
    'CUST-001',
    'SCHEDULED',
    'DHL',
    NULL,
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    NULL
);

-- ============================================================================
-- Notifications / Consumer
-- ============================================================================

-- Pending order-confirmation email for CUST-001
INSERT INTO notification (
    id,
    version,
    notification_id,
    customer_id,
    order_id,
    sku,
    type,
    channel,
    status,
    subject,
    body,
    recipient,
    created_at,
    sent_at,
    failed_at,
    failure_reason
)
VALUES (
    nextval('notification_seq'),
    0,
    'NOT-001',
    'CUST-001',
    'ORD-001',
    'AE-EDN-001',
    'ORDER_CONFIRMATION',
    'EMAIL',
    'PENDING',
    'Your Arcadia Editions order ORD-001 is confirmed',
    'Thank you for ordering Arcadia First Edition (AE-EDN-001).',
    'customer@example.com',
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    NULL
);
