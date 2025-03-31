## Add tables

1. `transactions` table:

```
CREATE TABLE transactions (
    id VARCHAR(100) PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date_time TIMESTAMP NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(10) NULL CHECK (type IN ('INCOME', 'EXPENSE', 'INVOICE', 'REFUND')),
    edited_by VARCHAR(10) NOT NULL CHECK (edited_by IN ('AUTO', 'USER')),
    due_date TIMESTAMP NULL,
    invoice_status VARCHAR(15) NULL CHECK (invoice_status IN ('CONFIRMED', 'UNCONFIRMED', 'CANCELED', 'PAID', 'UNPAID'))
);
```

2. `notifications` table:

```
CREATE TABLE notifications (
    id VARCHAR(100) PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    transaction_id VARCHAR(100) UNIQUE NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    date_time TIMESTAMP NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL
);
```
