# ApexMatch REST API Specification

ApexMatch exposes a high-performance RESTful API for submitting orders, executing trades, and querying market status.

---

## 1. Interactive Swagger / OpenAPI Documentation

When the ApexMatch server is running locally on port `8080`, comprehensive interactive documentation is accessible at:

- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI 3.0 JSON Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 2. Order Submission Endpoint

### `POST /api/orders`
Submits an order to the in-memory matching engine. If matching counter-orders exist in the order book, trades execute immediately and are returned in the response while being persisted asynchronously to the PostgreSQL database. If unmatched or partially filled, any remaining balance of a `LIMIT` order enters the book.

- **Content-Type**: `application/json`
- **Accept**: `application/json`

---

## 3. Request Schema (`OrderRequest`)

| Field | Type | Validation Rule | Description |
| :--- | :--- | :--- | :--- |
| `userId` | `String` | `@NotBlank(message = "userId must not be blank")` | Identifier of the trader submitting the order (e.g. `"trader-101"`). |
| `symbol` | `String` | `@NotBlank(message = "symbol must not be blank")` | Asset ticker symbol (e.g. `"AAPL"`, `"TSLA"`). Case-insensitive in engine. |
| `side` | `Side` | `@NotNull(message = "side must be BUY or SELL")` | Order side: `BUY` or `SELL`. |
| `type` | `OrderType` | `@NotNull(message = "type must be LIMIT or MARKET")` | Order execution type: `LIMIT` or `MARKET`. |
| `price` | `BigDecimal` | Required & `> 0` for `LIMIT`; Must be `null` for `MARKET` | Execution limit price. Precision up to 4 decimal places. |
| `quantity` | `Long` | `@NotNull`, `@Min(value = 1, message = "quantity must be > 0")` | Number of shares to execute. |

---

## 4. Response Schemas

### 4.1 Success Response: HTTP 200 OK
Returns a JSON array of zero or more `Trade` objects:

```json
[
  {
    "tradeId": "TRD-1",
    "symbol": "AAPL",
    "buyer": "ALICE",
    "seller": "BOB",
    "price": 149.50,
    "quantity": 100
  }
]
```
*(An empty array `[]` indicates the order was successfully received and placed into the resting order book without immediate match).*

### 4.2 Error Response: HTTP 400 Bad Request (`ErrorResponse`)
When request parameters or business constraints fail validation, ApexMatch returns a structured error object:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-09-07T14:32:00.123",
  "errors": {
    "field": "Specific failure reason"
  }
}
```

---

## 5. Concrete Request / Response Scenarios

### Scenario 1: Successful LIMIT Order That Rests (No Counter-Orders)
A trader places a passive sell order into an empty order book.

**Request:**
```http
POST /api/orders HTTP/1.1
Content-Type: application/json

{
  "userId": "ALICE",
  "symbol": "AAPL",
  "side": "SELL",
  "type": "LIMIT",
  "price": 150.00,
  "quantity": 100
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

[]
```

---

### Scenario 2: Successful LIMIT Order That Matches Immediately
A buyer arrives with a price willing to cross the resting ask (`$150.00 >= $150.00`).

**Request:**
```http
POST /api/orders HTTP/1.1
Content-Type: application/json

{
  "userId": "DAVID",
  "symbol": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "price": 150.00,
  "quantity": 60
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "tradeId": "TRD-1",
    "symbol": "AAPL",
    "buyer": "DAVID",
    "seller": "ALICE",
    "price": 150.00,
    "quantity": 60
  }
]
```

---

### Scenario 3: Successful MARKET Order Executing Against the Book
A buyer wants immediate liquidity regardless of price.

**Request:**
```http
POST /api/orders HTTP/1.1
Content-Type: application/json

{
  "userId": "EMMA",
  "symbol": "AAPL",
  "side": "BUY",
  "type": "MARKET",
  "price": null,
  "quantity": 40
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "tradeId": "TRD-2",
    "symbol": "AAPL",
    "buyer": "EMMA",
    "seller": "ALICE",
    "price": 150.00,
    "quantity": 40
  }
]
```

---

### Scenario 4: Validation Failure — Missing Price on LIMIT Order
A limit order submitted without specifying a price fails business validation.

**Request:**
```http
POST /api/orders HTTP/1.1
Content-Type: application/json

{
  "userId": "BOB",
  "symbol": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "price": null,
  "quantity": 50
}
```

**Response:**
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-09-07T14:35:10.450",
  "errors": {
    "price": "Price must be provided for LIMIT orders"
  }
}
```

---

### Scenario 5: Validation Failure — Price Provided on MARKET Order
A market order submitted with a price violates the market order definition.

**Request:**
```http
POST /api/orders HTTP/1.1
Content-Type: application/json

{
  "userId": "BOB",
  "symbol": "AAPL",
  "side": "BUY",
  "type": "MARKET",
  "price": 150.00,
  "quantity": 50
}
```

**Response:**
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-09-07T14:36:00.112",
  "errors": {
    "price": "Price must be null for MARKET orders"
  }
}
```

---

### Scenario 6: Validation Failure — Zero or Negative Quantity
Quantity must be strictly positive ($> 0$).

**Request:**
```http
POST /api/orders HTTP/1.1
Content-Type: application/json

{
  "userId": "BOB",
  "symbol": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "price": 150.00,
  "quantity": 0
}
```

**Response:**
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-09-07T14:37:05.789",
  "errors": {
    "quantity": "quantity must be > 0"
  }
}
```

---

### Scenario 7: Validation Failure — Blank User ID or Symbol
Jakarta Bean Validation rejects empty or whitespace-only identification fields.

**Request:**
```http
POST /api/orders HTTP/1.1
Content-Type: application/json

{
  "userId": "   ",
  "symbol": "",
  "side": "BUY",
  "type": "LIMIT",
  "price": 150.00,
  "quantity": 100
}
```

**Response:**
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-09-07T14:38:22.014",
  "errors": {
    "userId": "userId must not be blank",
    "symbol": "symbol must not be blank"
  }
}
```
