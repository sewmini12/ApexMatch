# ApexMatch REST API Specification

ApexMatch provides a RESTful interface for submitting orders to the matching engine.

---

## 1. Submit Order Endpoint

- **Endpoint**: `/api/orders`
- **HTTP Method**: `POST`
- **Content-Type**: `application/json`
- **Description**: Submits a new BUY or SELL order to the matching engine. If compatible counter-orders exist in the order book, executions occur immediately and a list of generated trades is returned. If no counter-orders are available, or only partial fills occur, any remaining LIMIT order balance is added to the in-memory order book.

---

## 2. Request Schema (`OrderRequest`)

| Field      | Type     | Required | Allowed Values    | Description                                              |
|:-----------|:---------|:---------|:------------------|:---------------------------------------------------------|
| `userId`   | String   | Yes      | Non-empty string  | Identifier of the trader placing the order               |
| `symbol`   | String   | Yes      | Non-empty string  | Ticker symbol of the asset (e.g., `AAPL`)                |
| `side`     | String   | Yes      | `BUY`, `SELL`     | Side of the market order                                 |
| `type`     | String   | Yes      | `LIMIT`, `MARKET` | Execution order type                                     |
| `price`    | Number   | Yes*     | Positive decimal  | Limit price (*Required for LIMIT, must be null for MARKET)|
| `quantity` | Integer  | Yes      | Positive integer  | Number of shares to trade                                |

*Note: Internal fields `orderId` and `sequenceNumber` are assigned automatically by the backend engine and are not accepted from the client.*

---

## 3. Step-by-Step Scenario & Expected Matching Behavior

### Step 1: Bob Submits a LIMIT SELL Order
Bob submits a passive SELL order offering 60 shares of AAPL at $145.00:

#### Request:
`POST /api/orders`
```json
{
  "userId": "BOB",
  "symbol": "AAPL",
  "side": "SELL",
  "type": "LIMIT",
  "price": 145.00,
  "quantity": 60
}
```

#### Behavior:
- The order book has no existing BUY orders.
- No trade is executed.
- Bob's order rests in the SELL book: `60 AAPL @ $145.00`.

#### Response:
- **HTTP Status**: `200 OK`
- **Body**:
```json
[]
```

---

### Step 2: Alice Submits a LIMIT BUY Order
Alice submits an aggressive BUY order for 100 shares of AAPL willing to pay up to $150.00:

#### Request:
`POST /api/orders`
```json
{
  "userId": "ALICE",
  "symbol": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "price": 150.00,
  "quantity": 100
}
```

#### Behavior:
- The engine finds Bob's resting order: `60 AAPL @ $145.00`.
- Price check: Alice's BUY limit (`$150.00`) $\ge$ Bob's SELL limit (`$145.00`). A match occurs.
- **Execution Price**: `$145.00` (the price of the resting passive order).
- **Execution Quantity**: `60` shares ($\min(100, 60)$).
- **Trade Created**: Buyer `ALICE`, Seller `BOB`, Price `145.00`, Quantity `60`.
- Bob's order is fully filled (0 remaining) and removed from the SELL book.
- Alice's order is partially filled (40 remaining). Since Alice's order is a LIMIT order, the remaining `40 AAPL @ $150.00` is added to the BUY order book.
- The trade is persisted to the database.

#### Response:
- **HTTP Status**: `200 OK`
- **Body**:
```json
[
  {
    "tradeId": "TRD-1",
    "symbol": "AAPL",
    "buyer": "ALICE",
    "seller": "BOB",
    "price": 145.00,
    "quantity": 60
  }
]
```

---

## 4. Response Status Codes

- `200 OK`: Order processed successfully; returns an array of zero or more `Trade` objects.
- `400 Bad Request`: (Planned) Request validation failure (e.g., negative quantity, missing user ID, or LIMIT order without price).
- `500 Internal Server Error`: Unexpected server-side failure.
