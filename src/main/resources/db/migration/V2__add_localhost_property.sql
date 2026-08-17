INSERT INTO properties (id, domain, created_at)
VALUES (
    gen_random_uuid(),
    'localhost',
    CURRENT_TIMESTAMP
);
