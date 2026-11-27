CREATE TABLE user_accounts (
    username VARCHAR(40) PRIMARY KEY,
    password_hash VARCHAR(60) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT ck_user_account_role CHECK (role IN ('ADMIN', 'OPERADOR'))
);
