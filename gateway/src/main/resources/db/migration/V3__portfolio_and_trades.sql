ALTER TABLE users
    ALTER COLUMN balance TYPE NUMERIC(18, 2) USING balance::NUMERIC,
    ALTER COLUMN balance SET DEFAULT 1000000.00;

UPDATE users SET balance = 1000000.00 WHERE balance = 0;

CREATE TABLE IF NOT EXISTS portfolio (
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    quote_name VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    avg_price NUMERIC(18, 2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, quote_name)
);

CREATE TABLE IF NOT EXISTS trades (
    trade_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    quote_name VARCHAR(32) NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price NUMERIC(18, 2) NOT NULL,
    total NUMERIC(18, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
