-- symbanta
--
-- Copyright © Josep Bigorra <jjbigorra@gmail.com>
--
-- symbanta is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- symbanta is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public License
-- along with symbanta.  If not, see <https://www.gnu.org/licenses/>.
--

-- Inserts for `properties` table (including the ID referenced in your sample)
INSERT INTO public.properties (id, domain, created_at, updated_at)
VALUES ('4fc2a369-d449-426e-ad9b-0d331fafa18e', 'my-site.com', '2026-01-15 10:00:00.000000+00',
        '2026-01-15 10:00:00.000000+00'),
       ('8f21e51b-0012-4c6e-93b5-7c2a1290a100', 'symbanta.io', '2026-02-01 08:30:00.000000+00',
        '2026-02-01 08:30:00.000000+00'),
       ('a1b2c3d4-e5f6-4a5b-8c9d-0123456789ab', 'acme-store.com', '2026-03-10 14:20:00.000000+00',
        '2026-05-12 11:15:00.000000+00');

-- Properties
INSERT INTO public.properties
    (id, domain, created_at, updated_at)
VALUES ('8c7f1e25-2c5a-4f1a-9b73-6e8d4a2c1f90',
        'shop.example.com',
        '2026-08-20 14:07:12+02',
        '2026-09-05 11:32:44+02'),
       ('c1a4e7b2-93d8-46f5-a0c1-7b2e9d4f8a63',
        'docs.example.org',
        '2026-08-27 09:45:33+02',
        NULL);

-- Additional inserts for `events` table across different user sessions and flows
INSERT INTO public.events (id, property_id, session_id, event_name, url, referrer, screen_width, screen_height,
                           language, user_agent, props, created_at)
VALUES
-- Continued session from your my-site.com instance (button click)
('f3d2a1b0-4c5e-6f7a-8b9c-0d1e2f3a4b5c', '4fc2a369-d449-426e-ad9b-0d331fafa18e', 'cf931600-2d6c-4719-8f6e-e9e7f0fd8878',
 'click', 'http://localhost:3001/instrumented-page.html', 'http://localhost:3001/', 1735, 1157, 'nl-NL',
 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
 '{"element_id": "submit-btn", "target": "form"}', '2026-09-07 06:17:15.102341+00'),

-- Symbanta.io Desktop Session (Organic Search -> Landing Page -> Signup)
('7c9e1234-e89b-12d3-a456-426614174000', '8f21e51b-0012-4c6e-93b5-7c2a1290a100', '9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d',
 'page-view', 'https://symbanta.io/', 'https://www.google.com/', 1920, 1080, 'en-US',
 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
 '{"utm_source": "google", "utm_medium": "organic"}', '2026-09-07 07:45:10.120450+00'),
('11223344-5566-7788-9900-aabbccddeeff', '8f21e51b-0012-4c6e-93b5-7c2a1290a100', '9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d',
 'click', 'https://symbanta.io/', 'https://www.google.com/', 1920, 1080, 'en-US',
 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
 '{"cta_text": "Start Free Trial", "position": "hero"}', '2026-09-07 07:45:22.481920+00'),
('33445566-7788-9900-aabb-ccddeeff1122', '8f21e51b-0012-4c6e-93b5-7c2a1290a100', '9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d',
 'signup', 'https://symbanta.io/register', 'https://symbanta.io/', 1920, 1080, 'en-US',
 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
 '{"plan": "free_tier", "method": "email"}', '2026-09-07 07:46:05.901233+00'),

-- Acme Store Mobile Session (E-commerce Pageview & Add to Cart)
('55667788-9900-aabb-ccdd-eeff11223344', 'a1b2c3d4-e5f6-4a5b-8c9d-0123456789ab', '2b3c4d5e-6f7a-8b9c-0d1e-2f3a4b5c6d7e',
 'page-view', 'https://acme-store.com/products/wireless-headphones', 'https://t.co/', 390, 844, 'de-DE',
 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
 '{"category": "electronics", "in_stock": true}', '2026-09-07 08:02:11.300124+00'),
('77889900-aabb-ccdd-eeff-112233445566', 'a1b2c3d4-e5f6-4a5b-8c9d-0123456789ab', '2b3c4d5e-6f7a-8b9c-0d1e-2f3a4b5c6d7e',
 'scroll-depth', 'https://acme-store.com/products/wireless-headphones', 'https://t.co/', 390, 844, 'de-DE',
 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
 '{"depth_percentage": 75}', '2026-09-07 08:02:25.874102+00'),
('9900aabb-ccdd-eeff-1122-334455667788', 'a1b2c3d4-e5f6-4a5b-8c9d-0123456789ab', '2b3c4d5e-6f7a-8b9c-0d1e-2f3a4b5c6d7e',
 'add-to-cart', 'https://acme-store.com/products/wireless-headphones', 'https://t.co/', 390, 844, 'de-DE',
 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
 '{"product_id": "sku-9921", "price": 149.99, "currency": "EUR"}', '2026-09-07 08:02:40.012891+00');


-- Example.com
INSERT INTO public.events
(id, property_id, session_id, event_name, url, referrer,
 screen_width, screen_height, language, user_agent, props, created_at)
VALUES ('1eba98c7-8287-47ec-8915-13f8644b69f1',
        '4fc2a369-d449-426e-ad9b-0d331fafa18e',
        'cf931600-2d6c-4719-8f6e-e9e7f0fd8878',
        'page-view',
        'https://example.com/',
        NULL,
        1920,
        1080,
        'en-US',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
        '{"page": "home", "theme": "light"}',
        '2026-09-07 06:15:02.120000+00'),
       ('b5ce03b1-1857-4044-a3a5-8368a455efdf',
        '4fc2a369-d449-426e-ad9b-0d331fafa18e',
        'cf931600-2d6c-4719-8f6e-e9e7f0fd8878',
        'visibility-change',
        'https://example.com/',
        NULL,
        1920,
        1080,
        'en-US',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
        '{"visible": false}',
        '2026-09-07 06:17:08.570997+00'),
       ('7d3a5f19-64c2-4e87-b1a9-32f6c8d04751',
        '4fc2a369-d449-426e-ad9b-0d331fafa18e',
        'a72f1c44-9e61-4b2a-83d7-51c6f09e28ab',
        'page-view',
        'https://example.com/pricing',
        'https://www.google.com/',
        1536,
        864,
        'nl-NL',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
        '{"page": "pricing", "plan": "pro"}',
        '2026-09-07 06:32:14.381000+00'),
       ('91e4b2c6-7f35-48a0-9d13-52c8f6e74120',
        '4fc2a369-d449-426e-ad9b-0d331fafa18e',
        'a72f1c44-9e61-4b2a-83d7-51c6f09e28ab',
        'click',
        'https://example.com/pricing',
        'https://www.google.com/',
        1536,
        864,
        'nl-NL',
        'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
        '{"element": "cta", "label": "Start free trial"}',
        '2026-09-07 06:32:19.774000+00'),
       ('d4f8a102-3b67-4c91-8e25-6a7d9f0b3145',
        '4fc2a369-d449-426e-ad9b-0d331fafa18e',
        'e31c9a72-54bf-4d68-b207-81f6a3c92510',
        'page-view',
        'https://example.com/docs/getting-started',
        'https://example.com/',
        2560,
        1440,
        'de-DE',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
        '{"page": "getting-started", "section": "installation"}',
        '2026-09-07 07:04:51.902000+00'),
       ('f62c8149-0a37-4e56-b921-73d8c5f10426',
        '4fc2a369-d449-426e-ad9b-0d331fafa18e',
        'e31c9a72-54bf-4d68-b207-81f6a3c92510',
        'signup',
        'https://example.com/signup',
        'https://example.com/docs/getting-started',
        2560,
        1440,
        'de-DE',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
        '{"source": "documentation", "method": "email"}',
        '2026-09-07 07:11:27.318000+00');


-- shop.example.com
INSERT INTO public.events
(id, property_id, session_id, event_name, url, referrer,
 screen_width, screen_height, language, user_agent, props, created_at)
VALUES ('2c91e7f4-58a3-4b62-9d10-73f6c845a201',
        '8c7f1e25-2c5a-4f1a-9b73-6e8d4a2c1f90',
        'b81f3d29-7c64-4e05-a921-56d8f0c43712',
        'page-view',
        'https://shop.example.com/',
        'https://example.com/',
        1440,
        900,
        'en-GB',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Safari/605.1.15',
        '{"page": "home"}',
        '2026-09-07 07:24:03.112000+00'),
       ('5a73c8d1-24f6-49b0-ae52-81c7d9036f14',
        '8c7f1e25-2c5a-4f1a-9b73-6e8d4a2c1f90',
        'b81f3d29-7c64-4e05-a921-56d8f0c43712',
        'page-view',
        'https://shop.example.com/products/keyboard',
        'https://shop.example.com/',
        1440,
        900,
        'en-GB',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Safari/605.1.15',
        '{"product": "keyboard", "category": "hardware"}',
        '2026-09-07 07:24:18.441000+00'),
       ('8e12f6a9-47c3-4d85-b201-63f7a9c52418',
        '8c7f1e25-2c5a-4f1a-9b73-6e8d4a2c1f90',
        'b81f3d29-7c64-4e05-a921-56d8f0c43712',
        'click',
        'https://shop.example.com/products/keyboard',
        'https://shop.example.com/',
        1440,
        900,
        'en-GB',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Safari/605.1.15',
        '{"element": "add-to-cart", "product": "keyboard"}',
        '2026-09-07 07:24:31.029000+00'),
       ('c47b912e-6a03-4f58-b823-15d7e9046c31',
        '8c7f1e25-2c5a-4f1a-9b73-6e8d4a2c1f90',
        'f92a6d14-38b7-45c1-9e20-74d5a8f63102',
        'page-view',
        'https://shop.example.com/checkout',
        'https://shop.example.com/cart',
        390,
        844,
        'nl-NL',
        'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1',
        '{"step": "checkout"}',
        '2026-09-07 07:51:44.603000+00'),
       ('e8053f71-19c4-46a2-b728-94d6f013c582',
        '8c7f1e25-2c5a-4f1a-9b73-6e8d4a2c1f90',
        'f92a6d14-38b7-45c1-9e20-74d5a8f63102',
        'purchase',
        'https://shop.example.com/checkout/complete',
        'https://shop.example.com/checkout',
        390,
        844,
        'nl-NL',
        'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1',
        '{"items": 2, "currency": "EUR"}',
        '2026-09-07 07:53:12.881000+00');


-- docs.example.org
INSERT INTO public.events
(id, property_id, session_id, event_name, url, referrer,
 screen_width, screen_height, language, user_agent, props, created_at)
VALUES ('3f82a614-9b57-4c20-ae31-75d8f604c291',
        'c1a4e7b2-93d8-46f5-a0c1-7b2e9d4f8a63',
        'd17c5a82-64f9-43b1-8e26-90a7c3145f68',
        'page-view',
        'https://docs.example.org/',
        'https://duckduckgo.com/',
        1920,
        1080,
        'fr-FR',
        'Mozilla/5.0 (X11; Linux x86_64; rv:142.0) Gecko/20100101 Firefox/142.0',
        '{"page": "documentation-home"}',
        '2026-09-07 08:01:17.442000+00'),
       ('6b14e9c2-37a5-4f80-b261-95d7a3e82410',
        'c1a4e7b2-93d8-46f5-a0c1-7b2e9d4f8a63',
        'd17c5a82-64f9-43b1-8e26-90a7c3145f68',
        'page-view',
        'https://docs.example.org/api/events',
        'https://docs.example.org/',
        1920,
        1080,
        'fr-FR',
        'Mozilla/5.0 (X11; Linux x86_64; rv:142.0) Gecko/20100101 Firefox/142.0',
        '{"page": "api-events", "version": "v1"}',
        '2026-09-07 08:02:49.817000+00'),
       ('a93c715e-62f8-4b04-9d31-57e6c8201f46',
        'c1a4e7b2-93d8-46f5-a0c1-7b2e9d4f8a63',
        'd17c5a82-64f9-43b1-8e26-90a7c3145f68',
        'click',
        'https://docs.example.org/api/events',
        'https://docs.example.org/',
        1920,
        1080,
        'fr-FR',
        'Mozilla/5.0 (X11; Linux x86_64; rv:142.0) Gecko/20100101 Firefox/142.0',
        '{"element": "copy-code", "language": "curl"}',
        '2026-09-07 08:03:11.205000+00');

INSERT INTO public.events (id, property_id, session_id, event_name, url, referrer, screen_width, screen_height,
                           language, user_agent, props, created_at)
VALUES
-- Session 1 (Continued from cf931600-2d6c-4719-8f6e-e9e7f0fd8878): User scrolling and toggling settings on Linux
('a7f1d8c2-3e4b-4a5c-8d9e-0f1a2b3c4d5e', '4fb1a369-d449-426e-ad9b-0d331fafa18e', 'cf931600-2d6c-4719-8f6e-e9e7f0fd8878',
 'scroll-depth', 'http://localhost:3001/instrumented-page.html', 'http://localhost:3001/', 1735, 1157, 'nl-NL',
 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
 '{"depth_percentage": 50, "time_on_page_ms": 4200}', '2026-09-07 06:17:12.410192+00'),
('b8e2f9d3-4a5b-5c6d-9e0f-1a2b3c4d5e6f', '4fb1a369-d449-426e-ad9b-0d331fafa18e', 'cf931600-2d6c-4719-8f6e-e9e7f0fd8878',
 'click', 'http://localhost:3001/instrumented-page.html', 'http://localhost:3001/', 1735, 1157, 'nl-NL',
 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
 '{"element_id": "theme-toggle", "selected_mode": "dark"}', '2026-09-07 06:17:18.902341+00'),
('c9f3e0a4-5b6c-6d7e-0f1a-2b3c4d5e6f7a', '4fb1a369-d449-426e-ad9b-0d331fafa18e', 'cf931600-2d6c-4719-8f6e-e9e7f0fd8878',
 'page-view', 'http://localhost:3001/checkout.html', 'http://localhost:3001/instrumented-page.html', 1735, 1157,
 'nl-NL', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
 '{"referrer_path": "/instrumented-page.html"}', '2026-09-07 06:17:25.011823+00'),
('d0a4f1b5-6c7d-7e8f-1a2b-3c4d5e6f7a8b', '4fb1a369-d449-426e-ad9b-0d331fafa18e', 'cf931600-2d6c-4719-8f6e-e9e7f0fd8878',
 'form-submit', 'http://localhost:3001/checkout.html', 'http://localhost:3001/instrumented-page.html', 1735, 1157,
 'nl-NL', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
 '{"form_id": "test-payment", "status": "success"}', '2026-09-07 06:17:42.654321+00'),

-- Session 2: Mobile viewport debugging/testing session
('e1b5a2c6-7d8e-8f9a-2b3c-4d5e6f7a8b9c', '4fb1a369-d449-426e-ad9b-0d331fafa18e', 'a8d29f41-3b1e-4c8d-9012-7f6e5d4c3b2a',
 'page-view', 'http://localhost:3001/index.html', NULL, 390, 844, 'en-US',
 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
 '{"viewport_scale": 1.0, "is_mobile": true}', '2026-09-07 07:00:10.123456+00'),
('f2c6b3d7-8e9f-9a0b-3c4d-5e6f7a8b9c0d', '4fb1a369-d449-426e-ad9b-0d331fafa18e', 'a8d29f41-3b1e-4c8d-9012-7f6e5d4c3b2a',
 'click', 'http://localhost:3001/index.html', NULL, 390, 844, 'en-US',
 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
 '{"element_id": "mobile-hamburger-menu"}', '2026-09-07 07:00:15.890123+00'),

-- Session 3: Automated testing / Headless browser event capture
('03d7c4e8-9f0a-0b1c-4d5e-6f7a8b9c0d1e', '4fb1a369-d449-426e-ad9b-0d331fafa18e', 'e1a72b83-90d4-42f1-bc89-112233445566',
 'page-view', 'http://localhost:3001/instrumented-page.html', 'http://localhost:3001/login', 1280, 720, 'en',
 'Mozilla/5.0 (HeadlessChrome/151.0.0.0 Safari/537.36)', '{"test_suite": "e2e_regression", "environment": "local"}',
 '2026-09-07 08:10:00.000000+00'),
('14e8d5f9-0a1b-1c2d-5e6f-7a8b9c0d1e2f', '4fb1a369-d449-426e-ad9b-0d331fafa18e', 'e1a72b83-90d4-42f1-bc89-112233445566',
 'app-error', 'http://localhost:3001/instrumented-page.html', 'http://localhost:3001/login', 1280, 720, 'en',
 'Mozilla/5.0 (HeadlessChrome/151.0.0.0 Safari/537.36)',
 '{"error_code": "ERR_NETWORK_TIMEOUT", "component": "analytics-tracker"}', '2026-09-07 08:10:02.451200+00');