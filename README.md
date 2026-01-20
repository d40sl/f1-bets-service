# F1 Bets

Production-grade REST API for Formula 1 betting. Built with Spring Boot 3.3, Java 17, and PostgreSQL.

## Quick Start

```bash
# Required: copy and configure environment
cp .env.example .env
# Edit .env - POSTGRES_PASSWORD must be set (no default)

# Start the application
docker compose up -d
```

The API will be available at `http://localhost:8090`.

> **Note:** For the sake of simplicity, this project does not include authentication. CORS is configured to allow common development origins. In a production environment, you would add proper authentication (e.g., JWT, OAuth2), authorization, and restrict CORS to trusted origins.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/events` | List F1 events with driver markets |
| `GET` | `/api/v1/users/{userId}` | Get user profile with balance and bets |
| `POST` | `/api/v1/bets` | Place a bet on a driver |
| `POST` | `/api/v1/events/{sessionKey}/settle` | Settle an event outcome |
| `GET` | `/actuator/health` | Health check endpoint |

### Headers

| Header | Required | Description | Example |
|--------|----------|-------------|---------|
| `X-User-Id` | Yes (for POST /bets) | Unique user identifier. Alphanumeric, hyphens, underscores. Max 100 chars. | `john-doe-123`, `user_42` |
| `Idempotency-Key` | Yes (for all POST) | UUID to prevent duplicate requests. Must be unique per distinct request. | `550e8400-e29b-41d4-a716-446655440000` |
| `Content-Type` | Yes (for POST) | Must be `application/json` | `application/json` |

### Query Parameters for GET /api/v1/events

| Parameter | Type | Required | Description | Valid Values |
|-----------|------|----------|-------------|--------------|
| `sessionType` | string | No | Type of F1 session | `Race`, `Qualifying`, `Sprint`, `Sprint Qualifying`, `Practice 1`, `Practice 2`, `Practice 3` |
| `year` | integer | No | Season year | `2023`, `2024`, `2025` |
| `countryCode` | string | No | ISO 3-letter country code | `GBR` (Britain), `ITA` (Italy), `MON` (Monaco), `AUS` (Australia), `USA` (United States), `JPN` (Japan), `BRA` (Brazil), `AUT` (Austria), `NLD` (Netherlands), `ESP` (Spain), `MEX` (Mexico), `SGP` (Singapore), `BEL` (Belgium), `HUN` (Hungary), `CAN` (Canada), `SAU` (Saudi Arabia), `BHR` (Bahrain), `ARE` (Abu Dhabi), `QAT` (Qatar), `CHN` (China), `MIA` (Miami), `LVS` (Las Vegas) |

### Common Driver Numbers (2024 Season)

| Number | Driver | Team |
|--------|--------|------|
| 1 | Max Verstappen | Red Bull Racing |
| 11 | Sergio Perez | Red Bull Racing |
| 44 | Lewis Hamilton | Mercedes |
| 63 | George Russell | Mercedes |
| 16 | Charles Leclerc | Ferrari |
| 55 | Carlos Sainz | Ferrari |
| 4 | Lando Norris | McLaren |
| 81 | Oscar Piastri | McLaren |
| 14 | Fernando Alonso | Aston Martin |
| 18 | Lance Stroll | Aston Martin |

## Quick Test Commands

Copy-paste these commands to quickly test the API:

```bash
# 1. Health check
curl -s http://localhost:8090/actuator/health | jq

# 2. List events (2024 races)
curl -s "http://localhost:8090/api/v1/events?year=2024&sessionType=Race" | jq

# 3. Place a bet on Verstappen (driver #1) - EUR 25.00
curl -s -X POST http://localhost:8090/api/v1/bets \
  -H "Content-Type: application/json" \
  -H "X-User-Id: test-reviewer" \
  -H "Idempotency-Key: $(uuidgen || cat /proc/sys/kernel/random/uuid)" \
  -d '{"sessionKey": 9158, "driverNumber": 1, "amount": 25.00}' | jq

# 4. Check user balance and bets
curl -s http://localhost:8090/api/v1/users/test-reviewer | jq

# 5. Place another bet on Hamilton (driver #44) - EUR 30.00
curl -s -X POST http://localhost:8090/api/v1/bets \
  -H "Content-Type: application/json" \
  -H "X-User-Id: test-reviewer" \
  -H "Idempotency-Key: $(uuidgen || cat /proc/sys/kernel/random/uuid)" \
  -d '{"sessionKey": 9158, "driverNumber": 44, "amount": 30.00}' | jq

# 6. Settle the event - Verstappen wins!
curl -s -X POST http://localhost:8090/api/v1/events/9158/settle \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen || cat /proc/sys/kernel/random/uuid)" \
  -d '{"winningDriverNumber": 1}' | jq

# 7. Check final user balance (should show winnings)
curl -s http://localhost:8090/api/v1/users/test-reviewer | jq
```

## Request/Response Examples

### Place a Bet

**POST /api/v1/bets**

Place a bet on a driver to win an F1 session. New users automatically receive EUR 100.00 starting balance.

| Field | Type | Required | Description | Constraints |
|-------|------|----------|-------------|-------------|
| `sessionKey` | integer | Yes | F1 session identifier from OpenF1 API | Positive integer |
| `driverNumber` | integer | Yes | Driver's racing number | 1-99 |
| `amount` | decimal | Yes | Bet amount in EUR | 0.01 - 10,000.00, max 2 decimal places |

**Request:**

```bash
curl -X POST http://localhost:8090/api/v1/bets \
  -H "Content-Type: application/json" \
  -H "X-User-Id: john-doe-123" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "sessionKey": 9158,
    "driverNumber": 1,
    "amount": 25.00
  }'
```

**Response (201 Created):**

```json
{
  "betId": "6a1e50e2-1c24-4fa2-8deb-4d20b5631642",
  "sessionKey": 9158,
  "driverNumber": 1,
  "stake": 25.00,
  "odds": 3,
  "potentialWinnings": 75.00,
  "status": "PENDING",
  "userBalance": 75.00
}
```

**Response Fields:**

| Field | Description |
|-------|-------------|
| `betId` | Unique bet identifier (UUID) |
| `sessionKey` | The F1 session this bet is for |
| `driverNumber` | The driver number bet on |
| `stake` | Amount wagered in EUR |
| `odds` | Server-computed odds (2, 3, or 4) |
| `potentialWinnings` | stake × odds if bet wins |
| `status` | `PENDING`, `WON`, or `LOST` |
| `userBalance` | User's balance after placing bet |

### Get User Profile

**GET /api/v1/users/{userId}**

Retrieve user profile including current balance and all bets.

**Request:**

```bash
curl http://localhost:8090/api/v1/users/john-doe-123
```

**Response (200 OK):**

```json
{
  "userId": "john-doe-123",
  "balance": 75.00,
  "bets": [
    {
      "betId": "6a1e50e2-1c24-4fa2-8deb-4d20b5631642",
      "sessionKey": 9158,
      "driverNumber": 1,
      "stake": 25.00,
      "odds": 3,
      "potentialWinnings": 75.00,
      "status": "PENDING",
      "userBalance": null
    }
  ]
}
```

### Settle Event

**POST /api/v1/events/{sessionKey}/settle**

Settle an event by declaring the winning driver. All pending bets for this session are resolved:
- Bets on the winning driver → status `WON`, payout credited to user balance
- Bets on other drivers → status `LOST`, no payout

**⚠️ Important:** Events can only be settled once. Subsequent attempts return `409 Conflict`.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `winningDriverNumber` | integer | Yes | The driver number who won (1-99) |

**Request:**

```bash
curl -X POST http://localhost:8090/api/v1/events/9158/settle \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: a1b2c3d4-e5f6-7890-abcd-ef1234567890" \
  -d '{
    "winningDriverNumber": 1
  }'
```

**Response (200 OK):**

```json
{
  "sessionKey": 9158,
  "winningDriverNumber": 1,
  "totalBets": 5,
  "winningBets": 2,
  "totalPayout": 150.00
}
```

**Response Fields:**

| Field | Description |
|-------|-------------|
| `sessionKey` | The settled F1 session |
| `winningDriverNumber` | The winning driver |
| `totalBets` | Number of bets settled |
| `winningBets` | Number of winning bets |
| `totalPayout` | Total EUR paid out to winners |

### List Events

**GET /api/v1/events**

List F1 events from OpenF1 API with driver markets and computed odds.

**Request Examples:**

```bash
# All 2024 races
curl "http://localhost:8090/api/v1/events?year=2024&sessionType=Race"

# British GP sessions
curl "http://localhost:8090/api/v1/events?countryCode=GBR&year=2024"

# All qualifying sessions
curl "http://localhost:8090/api/v1/events?sessionType=Qualifying"
```

**Response (200 OK):**

```json
[
  {
    "sessionKey": 9158,
    "sessionName": "Race",
    "sessionType": "Race",
    "circuitName": "Silverstone Circuit",
    "countryName": "Great Britain",
    "countryCode": "GBR",
    "dateStart": "2024-07-07T14:00:00Z",
    "dateEnd": "2024-07-07T16:00:00Z",
    "year": 2024,
    "drivers": [
      {
        "driverNumber": 1,
        "fullName": "Max Verstappen",
        "teamName": "Red Bull Racing",
        "odds": 2
      },
      {
        "driverNumber": 44,
        "fullName": "Lewis Hamilton",
        "teamName": "Mercedes",
        "odds": 3
      },
      {
        "driverNumber": 16,
        "fullName": "Charles Leclerc",
        "teamName": "Ferrari",
        "odds": 4
      }
    ]
  }
]
```

### Error Responses

**402 Payment Required - Insufficient Balance:**

```json
{
  "timestamp": "2024-07-07T14:30:00Z",
  "status": 402,
  "error": "Payment Required",
  "message": "Insufficient balance: current=EUR 75.00, required=EUR 100.00",
  "path": "/api/v1/bets"
}
```

**400 Bad Request - Validation Error:**

```json
{
  "timestamp": "2024-07-07T14:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Driver number exceeds maximum of 99: 100",
  "path": "/api/v1/bets"
}
```

**409 Conflict - Event Already Settled:**

```json
{
  "timestamp": "2024-07-07T14:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Event already settled: sessionKey=9158",
  "path": "/api/v1/events/9158/settle"
}
```

**409 Conflict - Idempotency Key Reused with Different Request:**

```json
{
  "error": "Idempotency key conflict",
  "message": "Key already used with different request"
}
```

**404 Not Found - User Not Found:**

```json
{
  "timestamp": "2024-07-07T14:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "User not found: unknown-user",
  "path": "/api/v1/users/unknown-user"
}
```

## Security Rationale

### Money as Integer Cents (Not BigDecimal)

Money is represented internally as integer cents (`long`) rather than `BigDecimal`:

- **No rounding errors**: 2500 cents is exactly EUR 25.00. Floating-point arithmetic can produce 24.999999... or 25.000001, causing reconciliation nightmares.
- **Deterministic calculations**: `stakeCents * odds = exactPayout`. No need for rounding modes or precision decisions.
- **Database-native**: PostgreSQL `BIGINT` with `CHECK` constraints enforces bounds at the database level.
- **Auditable**: Ledger entries are unambiguous. EUR 25.00 is always 2500, never 2499 or 2501.

`BigDecimal` appears only at the API boundary for JSON serialization/deserialization.

### Odds Generation

The specification states odds should be "a random integer between 2, 3, and 4."

The implementation uses a **pseudo-random, deterministic approach** where odds are derived from a hash of `(sessionKey, driverNumber, seed)`. This ensures:

1. **Consistency** - The same driver in the same session always displays the same odds, preventing confusion when users refresh the page or revisit an event
2. **Fairness** - Odds shown at listing time are guaranteed to match odds at bet placement
3. **Auditability** - Odds can be independently verified and reproduced for dispute resolution
4. **Distribution** - Odds are evenly distributed across {2, 3, 4} based on the hash, appearing random to users while remaining predictable for the system

This avoids exposing randomness to clients while keeping the implementation simple and auditable.

### Pessimistic Locking

The `PlaceBetUseCase` acquires an exclusive lock on the user row before checking balance:

```java
userRepository.findByIdForUpdate(userId)  // SELECT ... FOR UPDATE
```

**Why pessimistic over optimistic:**
- **Prevents double-spend**: Without locking, two concurrent bets could both pass the balance check and overdraw the account.
- **Immediate feedback**: Users get instant success/failure rather than retry loops.
- **Simpler error handling**: No need to handle `OptimisticLockException` and re-read state.

### Append-Only Ledger

The `ledger_entries` table is protected by a database trigger that prevents `UPDATE` and `DELETE`:

```sql
CREATE TRIGGER ledger_immutability_guard
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW
    EXECUTE FUNCTION prevent_ledger_modification();
```

**Why append-only:**
- **Audit trail integrity**: Complete history of all financial transactions is preserved.
- **Regulatory compliance**: Betting systems require immutable records for dispute resolution.
- **Tamper evidence**: Any modification attempt is blocked at the database level, not just the application.

### Idempotency Keys for POST

All state-changing operations require an `Idempotency-Key` header:

```bash
curl -H "Idempotency-Key: abc-123" ...
```

**Prevents replay attacks:**
- Network retries won't create duplicate bets
- Malicious replay of captured requests is blocked
- Keys expire after 24 hours
- Reusing a key with different request body returns `409 Conflict`

## Architecture

The codebase follows Clean Architecture with four layers:

```
src/main/java/com/f1bets/
├── api/                    # Interface Adapters (Controllers, DTOs)
│   ├── controller/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── exception/
│   └── filter/
├── application/            # Use Cases
│   ├── dto/
│   └── usecase/
├── domain/                 # Enterprise Business Rules
│   ├── exception/
│   ├── model/
│   └── repository/
└── infrastructure/         # Frameworks & Drivers
    ├── config/
    ├── external/
    └── persistence/
```

**Layer responsibilities:**
- **Domain**: Core business logic (`Money`, `Odds`, `Bet`, `User`). Zero external dependencies.
- **Application**: Use case orchestration (`PlaceBetUseCase`, `SettleEventUseCase`). Coordinates domain objects.
- **API**: HTTP interface. Validates input, transforms DTOs, handles errors.
- **Infrastructure**: Database access, external API clients (OpenF1), configuration.

## Configuration

Copy `.env.example` to `.env` and customize values for your environment:

```bash
cp .env.example .env
```

### Environment Variables

**Required variables (no defaults):**

| Variable | Description |
|----------|-------------|
| `POSTGRES_USER` | Database username |
| `POSTGRES_PASSWORD` | Database password |
| `POSTGRES_DB` | Database name |

**Optional variables (have defaults):**

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_PORT` | `8090` | Application HTTP port |
| `APP_EXTERNAL_PORT` | `8090` | External port mapping for app |
| `DB_EXTERNAL_PORT` | `5432` | External port mapping for database |
| `POSTGRES_HOST` | `f1bets-db` | Database hostname |
| `POSTGRES_PORT` | `5432` | Database port |
| `OPENF1_BASE_URL` | `https://api.openf1.org/v1` | OpenF1 API base URL |
| `OPENF1_CACHE_TTL` | `180` | Cache TTL in seconds |
| `ODDS_SEED` | `F1BETS_SEED` | Seed for deterministic odds calculation |
| `LOG_LEVEL_APP` | `INFO` | Application log level |

### Business Constraints

| Constraint | Value |
|------------|-------|
| Initial user balance | EUR 100.00 (10,000 cents) |
| Maximum stake | EUR 10,000.00 (1,000,000 cents) |
| Valid odds | 2, 3, or 4 |
| Idempotency key expiry | 24 hours |

## Running Tests

### Via Docker (Recommended)

No local Java/Maven required. Runs all 173 unit and integration tests in isolated containers:

```bash
docker compose -f docker-compose.test.yml up --abort-on-container-exit --exit-code-from test-runner
```

### Smoke Test

Run the 57-assertion smoke test against a running instance:

```bash
# Start the app
docker compose up -d

# Run smoke test (uses 180s cache)
./smoke-test.sh

# Run smoke test (bypass cache, fresh OpenF1 requests)
./smoke-test.sh --no-cache
```

### Via Maven (Requires Java 17)

```bash
# Run all tests (requires Docker for Testcontainers)
./mvnw test

# Run only unit tests
./mvnw test -Dtest="*Test"

# Run integration tests
./mvnw test -Dtest="*IntegrationTest"
```

Tests use Testcontainers for PostgreSQL, ensuring tests run against a real database.

## Tech Stack

- Java 17
- Spring Boot 3.3
- PostgreSQL 16
- Flyway (migrations)
- Resilience4j (circuit breaker, retry)
- Caffeine (caching)
- Testcontainers (integration tests)
- OpenAPI/Swagger (API documentation available at `/swagger-ui.html`)
