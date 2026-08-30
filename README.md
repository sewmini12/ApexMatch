# ApexMatch

**A high-performance real-time order matching engine built with Java.**

ApexMatch is a backend financial-engine simulation that processes buy and sell orders using **Price-Time Priority (FIFO)**. It maintains in-memory order books and matches compatible orders based on price and arrival sequence.

## Current Features

* Buy and sell order models
* Price-Time Priority matching design
* In-memory order books using Java `PriorityQueue`
* Separate BUY and SELL priority handling
* Partial order quantity management

## Planned Features

* Complete matching engine
* Thread-safe concurrent order processing
* Spring Boot REST API
* PostgreSQL trade ledger
* Asynchronous trade persistence
* JUnit 5 and Mockito testing
* Docker support

## Tech Stack

* **Java 21**
* **Maven**
* **Spring Boot** *(planned)*
* **PostgreSQL** *(planned)*
* **JUnit 5 & Mockito** *(planned)*

## Core Concept

```text
Incoming Order
      ↓
Order Book
      ↓
Price-Time Priority
      ↓
Matching Engine
      ↓
Trade Execution
      ↓
Trade Ledger
```

> This project is an educational simulation of an electronic trading order-matching system and does not connect to real financial markets.
