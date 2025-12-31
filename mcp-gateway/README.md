# Pilvetark Payment Gateway

A minimal payment system MVP with Stripe integration and a clean Estonian UI.

## What Is This?

A production-ready payment gateway that:

- **Accepts payments** - Secure Stripe Checkout integration
- **Tracks status** - Real-time payment status via webhooks
- **Stores history** - Database persistence with Liquibase migrations
- **Simple UI** - Clean Estonian-language payment form

## Quick Start

### 1. Configure Stripe

Get your Stripe test keys from [Stripe Dashboard](https://dashboard.stripe.com/test/apikeys):

```bash
export STRIPE_SECRET_KEY=sk_test_...
export STRIPE_WEBHOOK_SECRET=whsec_...  # Optional for local dev
```

### 2. Run the Application

```bash
./gradlew bootRun
```

Open **http://localhost:8080** in your browser.

### 3. Test a Payment

1. Enter any email address
2. Enter an amount (min 0.50 EUR)
3. Click "Maksa"
4. Use Stripe test card: `4242 4242 4242 4242`
   - Any future expiry date
   - Any 3-digit CVC
   - Any billing address

## Stripe Test Mode

### Test Card Numbers

| Card Number | Description |
|-------------|-------------|
| `4242 4242 4242 4242` | Succeeds |
| `4000 0000 0000 0002` | Declined |
| `4000 0025 0000 3155` | Requires 3D Secure |

Use any future expiry date, any 3-digit CVC, and any postal code.

### Webhook Testing (Local Development)

For local webhook testing, use [Stripe CLI](https://stripe.com/docs/stripe-cli):

```bash
# Install Stripe CLI
brew install stripe/stripe-cli/stripe

# Login
stripe login

# Forward webhooks to local server
stripe listen --forward-to localhost:8080/api/payments/webhook

# The CLI will output a webhook secret like whsec_...
# Set it as environment variable:
export STRIPE_WEBHOOK_SECRET=whsec_...
```

## API Endpoints

### Create Checkout Session

```bash
POST /api/payments/checkout
Content-Type: application/json
X-API-Key: dev-key

{
  "email": "customer@example.com",
  "amount": 25.00,
  "currency": "EUR"
}
```

Response:
```json
{
  "ok": true,
  "paymentId": "uuid-here",
  "checkoutUrl": "https://checkout.stripe.com/...",
  "status": "PENDING"
}
```

### Get Payment Status

```bash
GET /api/payments/{paymentId}
X-API-Key: dev-key
```

### Refresh Payment Status

```bash
POST /api/payments/{paymentId}/refresh
X-API-Key: dev-key
```

### Stripe Webhook

```bash
POST /api/payments/webhook
Stripe-Signature: t=...,v1=...

# Raw webhook payload
```

## Payment Flow

```
1. User enters email + amount
2. Frontend calls POST /api/payments/checkout
3. Backend creates Stripe Checkout Session
4. User redirects to Stripe Checkout
5. User completes payment
6. Stripe sends webhook → backend updates status
7. User returns to success page
8. Frontend polls /refresh to confirm status
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `STRIPE_SECRET_KEY` | Stripe secret key (required) | - |
| `STRIPE_WEBHOOK_SECRET` | Webhook signature secret | - |
| `APP_BASE_URL` | Base URL for redirects | `http://localhost:8080` |
| `DATABASE_URL` | Database JDBC URL | H2 in-memory |
| `MCP_SECURITY_API_KEY` | API key for endpoints | `dev-key` |

### Database

Default: H2 in-memory database (data lost on restart).

For PostgreSQL:
```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/payments
export DATABASE_USER=postgres
export DATABASE_PASSWORD=secret
export DATABASE_DRIVER=org.postgresql.Driver
export HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
```

## Database Schema

### payment

| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR(36) | Primary key (UUID) |
| email | VARCHAR(255) | Customer email |
| amount | DECIMAL(10,2) | Payment amount |
| currency | VARCHAR(3) | Currency code (EUR) |
| status | VARCHAR(32) | PENDING, COMPLETED, FAILED, CANCELLED |
| stripe_session_id | VARCHAR(255) | Stripe Checkout Session ID |
| stripe_payment_intent_id | VARCHAR(255) | Stripe PaymentIntent ID |
| checkout_url | VARCHAR(1024) | Stripe Checkout URL |
| created_at | TIMESTAMP | Creation time |
| updated_at | TIMESTAMP | Last update time |
| completed_at | TIMESTAMP | Completion time |

### payment_event

| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR(36) | Primary key (UUID) |
| payment_id | VARCHAR(36) | Foreign key to payment |
| event_type | VARCHAR(64) | Stripe event type |
| provider_event_id | VARCHAR(128) | Stripe event ID (unique) |
| payload | TEXT | Raw webhook payload |
| created_at | TIMESTAMP | Event time |
| processed | BOOLEAN | Processing status |

## Production Deployment

### Checklist

- [ ] Set `STRIPE_SECRET_KEY` (live key: `sk_live_...`)
- [ ] Set `STRIPE_WEBHOOK_SECRET` from Stripe Dashboard
- [ ] Set `APP_BASE_URL` to your production domain
- [ ] Configure PostgreSQL database
- [ ] Set secure `MCP_SECURITY_API_KEY`
- [ ] Enable HTTPS (required for Stripe)
- [ ] Register webhook URL in Stripe Dashboard

### Stripe Webhook Registration

1. Go to [Stripe Webhooks](https://dashboard.stripe.com/webhooks)
2. Click "Add endpoint"
3. URL: `https://your-domain.com/api/payments/webhook`
4. Events to listen:
   - `checkout.session.completed`
   - `checkout.session.expired`
   - `payment_intent.succeeded`
   - `payment_intent.payment_failed`

## Project Structure

```
mcp-gateway/
├── src/main/java/com/example/mcp/
│   ├── payment/
│   │   ├── controller/   # Payment endpoints
│   │   ├── model/        # Payment, PaymentEvent entities
│   │   ├── repository/   # JPA repositories
│   │   └── service/      # Stripe integration
│   ├── config/           # Configuration classes
│   ├── controller/       # Other endpoints
│   ├── filter/           # API key authentication
│   └── service/          # Business logic
├── src/main/resources/
│   ├── db/changelog/     # Liquibase migrations
│   ├── static/           # Frontend
│   └── application.yml   # Configuration
└── build.gradle
```

## Requirements

- Java 17+
- Stripe account (test or live)

## License

MIT
