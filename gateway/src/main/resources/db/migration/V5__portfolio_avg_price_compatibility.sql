ALTER TABLE portfolio
    ADD COLUMN IF NOT EXISTS avg_price NUMERIC(18, 2) NOT NULL DEFAULT 0;

UPDATE portfolio
SET avg_price = average_price
WHERE avg_price = 0
  AND EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_name = 'portfolio'
        AND column_name = 'average_price'
  );
