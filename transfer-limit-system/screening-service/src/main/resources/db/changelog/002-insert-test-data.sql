--liquibase formatted sql

--changeset developer:002-insert-test-data

INSERT INTO blocked_senders (sender_id, reason, blocked_at) VALUES
(5,  'Fraud detected', NOW()),
(13, 'Sanctions list', NOW()),
(21, 'Suspicious activity', NOW()),
(34, 'Identity theft report', NOW()),
(42, 'Court order', NOW());
