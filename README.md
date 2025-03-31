## Add tables

1. `users` table:
```
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL CHECK (CHAR_LENGTH(password) >= 4),
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    date_of_birth DATE NOT NULL,
    name VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NOT NULL
);
```

2. `transactions` table:

```
CREATE TABLE transactions (
    id VARCHAR(100) PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date_time TIMESTAMP NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(10) NULL CHECK (type IN ('INCOME', 'EXPENSE', 'INVOICE', 'REFUND', 'TRANSFER', 'DIVIDEND')),
    edited_by VARCHAR(10) NOT NULL CHECK (edited_by IN ('AUTO', 'USER')),
    due_date TIMESTAMP NULL,
    invoice_status VARCHAR(15) NULL CHECK (invoice_status IN ('CONFIRMED', 'UNCONFIRMED', 'CANCELED', 'PAID', 'UNPAID'))
);
```

3. `notifications` table:

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
