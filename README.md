# 🛒 Shopping Cart Restriction Rule Engine

A **production-ready, extensible rule engine** built in **Java 21** that enforces Tesco's legal and business regulations on shopping cart/basket orders. The engine evaluates a set of configurable restriction rules against each order and returns a clear **`MET`** or **`BREACHED`** status.

---

## 📋 Table of Contents

1. [Business Context](#-business-context)
2. [Requirements Example](#-requirements-example)
3. [Project Structure](#-project-structure)
4. [Architecture & Design](#-architecture--design)
5. [Java 21 Features Used](#-java-21-features-used)
6. [Component Reference](#-component-reference)
7. [Rule Reference](#-rule-reference)
8. [How the Engine Works](#-how-the-engine-works)
9. [Getting Started](#-getting-started)
10. [Running the Demo](#-running-the-demo)
11. [Running Tests](#-running-tests)
12. [Adding a New Rule](#-adding-a-new-rule)
13. [Design Patterns](#-design-patterns)
14. [Tech Stack](#-tech-stack)

---

## 🏢 Business Context

Tesco receives **millions of orders every day** with an average basket size of 100 items. To comply with legal regulations and internal business policy, every order must be validated against a set of **restriction rules** before it is accepted. These rules are mandatory for all order transactions, both online and in-store.

The engine is designed to:
- Evaluate **all rules** before returning a result (no short-circuit) — so customers see **every** violation in a single response rather than one at a time.
- Be **thread-safe** and **stateless** — a single engine instance can serve millions of concurrent requests.
- Be **open for extension** — new rules can be added without touching existing code.

---

## 📦 Requirements Example

### Input — Shopping Cart

| Item   | Product ID | Category     | Quantity |
|--------|------------|--------------|----------|
| Item-1 | 1          | Paracetamol  | 3        |
| Item-2 | 2          | analgesic    | 3        |
| Item-3 | 3          | chocolate    | 8        |
| Item-4 | 4          | Paracetamol  | 2        |

### Business Restriction Rules

| Rule Name              | Description                                              |
|------------------------|----------------------------------------------------------|
| `BulkBuyLimit`         | Cannot buy more than **10** units of any single product  |
| `BulkBuyLimitCategory` | Cannot buy more than **5** units of **Paracetamol** total|

### Output

```
BulkBuyLimit        → all products ≤ 10              → MET  ✓
BulkBuyLimitCategory → Paracetamol total = 3+2 = 5 ≤ 5 → MET  ✓

RESTRICTION STATUS → MET
```

---

## 📁 Project Structure

```
shopping-cart-rule-service/
├── pom.xml                                              ← Maven build (Java 21)
└── src/
    ├── main/
    │   ├── java/com/tesco/restriction/
    │   │   │
    │   │   ├── model/                                   ← Immutable domain objects
    │   │   │   ├── CartItem.java                        ← A single basket line item
    │   │   │   ├── ShoppingCart.java                    ← The full basket + aggregation helpers
    │   │   │   ├── RestrictionStatus.java               ← Enum: MET | BREACHED
    │   │   │   └── RuleViolation.java                   ← Describes one specific violation
    │   │   │
    │   │   ├── rule/                                    ← Rule contract & abstractions
    │   │   │   ├── RestrictionRule.java                 ← Interface every rule must implement
    │   │   │   ├── RuleContext.java                     ← Runtime context passed to rules
    │   │   │   ├── RuleResult.java                      ← Sealed type: Compliant | Violated
    │   │   │   └── impl/                                ← Concrete rule implementations
    │   │   │       ├── BulkBuyLimitRule.java            ← Max qty per individual product
    │   │   │       └── BulkBuyLimitCategoryRule.java    ← Max aggregate qty per category
    │   │   │
    │   │   ├── engine/                                  ← Orchestration layer
    │   │   │   ├── RuleEngine.java                      ← Evaluates all rules; fluent Builder
    │   │   │   └── RuleEngineReport.java                ← Aggregated results + summary
    │   │   │
    │   │   └── RestrictionRuleEngineApp.java            ← Runnable demo (5 scenarios)
    │   │
    │   └── resources/
    │       └── logback.xml                              ← SLF4J logging configuration
    │
    └── test/
        └── java/com/tesco/restriction/
            ├── rule/impl/
            │   ├── BulkBuyLimitRuleTest.java            ← 18 unit tests
            │   └── BulkBuyLimitCategoryRuleTest.java    ← 14 unit tests
            └── engine/
                └── RuleEngineTest.java                  ← 10 integration tests
                                                            (42 tests total, all passing)
```

---

## 🏛️ Architecture & Design

The engine follows a clean layered architecture with strict separation of concerns:

```
┌─────────────────────────────────────────────────────────┐
│                   CLIENT / ORDER SERVICE                │
└────────────────────────┬────────────────────────────────┘
                         │  ShoppingCart
                         ▼
┌─────────────────────────────────────────────────────────┐
│                     RULE ENGINE                         │
│                                                         │
│   RuleEngine.builder()                                  │
│       .withRule(new BulkBuyLimitRule(10))               │
│       .withRule(new BulkBuyLimitCategoryRule(...))      │
│       .build()                                          │
│                                                         │
│   engine.evaluate(cart) ──► RuleEngineReport            │
└──────────┬──────────────────────────────────────────────┘
           │  RuleContext
           ▼
┌──────────────────────────────────────────────────────────┐
│                    RESTRICTION RULES                     │
│                                                          │
│  ┌─────────────────────┐  ┌──────────────────────────┐  │
│  │   BulkBuyLimitRule  │  │ BulkBuyLimitCategoryRule │  │
│  │   priority = 10     │  │   priority = 20          │  │
│  └─────────────────────┘  └──────────────────────────┘  │
│         ▼ RuleResult.Compliant / RuleResult.Violated     │
└──────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────────────────┐
│                   RuleEngineReport                       │
│                                                          │
│   overallStatus()    → MET | BREACHED                    │
│   breachedRules()    → List<RuleResult>                  │
│   allViolations()    → List<RuleViolation>               │
│   summary()          → Human-readable text               │
└──────────────────────────────────────────────────────────┘
```

### Key Principles

| Principle | How It's Applied |
|-----------|-----------------|
| **Open/Closed** | New rules implement `RestrictionRule`; the engine never changes |
| **Single Responsibility** | Each rule class owns exactly one restriction logic |
| **Dependency Inversion** | Engine depends on `RestrictionRule` interface, not concrete classes |
| **Immutability** | All domain objects are Java records (inherently immutable) |
| **Thread Safety** | `RuleEngine` is immutable after construction — safe for concurrent use |

---

## ☕ Java 21 Features Used

| Feature | Where | Purpose |
|---------|-------|---------|
| **Records** | `CartItem`, `ShoppingCart`, `RuleContext`, `RuleResult.Compliant`, `RuleResult.Violated`, `RuleEngineReport`, `RuleViolation` | Concise, immutable value objects — no boilerplate getters/constructors |
| **Sealed Interface** | `RuleResult` | Discriminated union — compiler guarantees exhaustive handling in switch |
| **Pattern Matching Switch** | `RuleEngineReport.summary()`, `RuleResult.violations()` | Type-safe, no casting; compiler warns on missing cases |
| **Compact Constructors** | All records | Validate invariants at construction time, before the object exists |
| **Stream API** | Rules, engine, report | Expressive, functional-style aggregation and filtering |
| **Text formatting** | `String.formatted()` throughout | Readable inline string templates |
| **`List.copyOf()`** | `ShoppingCart`, `RuleResult.Violated`, `RuleEngineReport` | Defensive immutable copies |

### Sealed Interface in Action

```java
// RuleResult is a sealed interface — only Compliant and Violated are permitted
public sealed interface RuleResult permits RuleResult.Compliant, RuleResult.Violated {
    record Compliant(String ruleName) implements RuleResult { ... }
    record Violated(String ruleName, List<RuleViolation> violations) implements RuleResult { ... }
}

// At the call site — exhaustive switch, no default needed, compiler-checked:
switch (result) {
    case RuleResult.Compliant ignored -> System.out.println("✓ No violations");
    case RuleResult.Violated violated -> violated.violations().forEach(System.out::println);
}
```

---

## 📘 Component Reference

### `CartItem` (record)

Represents a single line item in a shopping basket.

```java
new CartItem("1", "Paracetamol", 3)
// productId="1", category="Paracetamol", quantity=3
```

| Field | Type | Description |
|-------|------|-------------|
| `productId` | `String` | Unique product identifier |
| `category` | `String` | Product category (e.g. "Paracetamol", "chocolate") |
| `quantity` | `int` | Number of units in the basket (must be ≥ 1) |

**Validation** (compact constructor): `productId`/`category` must be non-blank; `quantity` must be ≥ 1.

---

### `ShoppingCart` (record)

The full basket. Provides aggregation helpers used by rules.

```java
ShoppingCart cart = new ShoppingCart("ORDER-001", List.of(item1, item2, item3));

cart.totalQuantityForCategory("Paracetamol"); // sum of all Paracetamol items
cart.quantityByCategory();                    // Map<category, totalQty>
cart.totalQuantity();                         // grand total of all quantities
cart.lineItemCount();                         // number of distinct product lines
```

**Validation**: `orderId` must be non-blank; `items` must be non-empty. The list is defensively copied (immutable).

---

### `RestrictionStatus` (enum)

```java
RestrictionStatus.MET      // basket is compliant — all rules satisfied
RestrictionStatus.BREACHED // basket violates one or more rules
```

---

### `RuleViolation` (record)

Describes a single specific violation detected by a rule.

| Field | Type | Description |
|-------|------|-------------|
| `subject` | `String` | What was checked (e.g. `"productId=3"`, `"category=paracetamol"`) |
| `actualValue` | `int` | The quantity found in the basket |
| `limitValue` | `int` | The configured maximum that was exceeded |
| `message` | `String` | Human-readable violation description |

```java
// Convenience factories:
RuleViolation.forProduct("3", 12, 10);
// → "Product '3' has quantity 12 which exceeds the per-product limit of 10"

RuleViolation.forCategory("Paracetamol", 7, 5);
// → "Category 'Paracetamol' has total quantity 7 which exceeds the category limit of 5"
```

---

### `RestrictionRule` (interface)

The **extension point** for all rules.

```java
public interface RestrictionRule {
    String getName();          // stable rule identifier (e.g. "BulkBuyLimit")
    String getDescription();   // human-readable description
    RuleResult evaluate(RuleContext context);  // core evaluation logic
    default int priority() { return 100; }    // evaluation order (lower = first)
}
```

---

### `RuleContext` (record)

The context passed to every rule during evaluation. Decouples rules from the raw `ShoppingCart` and allows future metadata (customer tier, store type, feature flags) to be injected without changing the `RestrictionRule` interface.

```java
RuleContext context = new RuleContext(cart);

// With runtime metadata (e.g. for future age-gating rules):
RuleContext context = new RuleContext(cart, Map.of("customerAge", 17, "storeType", "online"));
context.getMetadata("customerAge", Integer.class, 0); // → 17
```

---

### `RuleResult` (sealed interface)

The return type of `RestrictionRule.evaluate()`. Two permitted sub-types:

```java
// The cart satisfies the rule:
RuleResult.compliant("BulkBuyLimit");

// The cart violates the rule:
RuleResult.violated("BulkBuyLimit", List.of(violation1, violation2));
```

Helper methods (no casting needed):
```java
result.isCompliant();   // true if Compliant
result.isViolated();    // true if Violated
result.status();        // RestrictionStatus.MET or BREACHED
result.violations();    // empty list if compliant; violation list if violated
```

---

### `RuleEngine` (class)

The **orchestrator**. Immutable after construction. Thread-safe.

```java
RuleEngine engine = RuleEngine.builder()
    .withRule(new BulkBuyLimitRule(10))
    .withRule(new BulkBuyLimitCategoryRule("Paracetamol", 5))
    .build();

RuleEngineReport report = engine.evaluate(cart);
```

- Rules are **sorted by `priority()` at build time** — no sorting overhead per request.
- **All rules are always evaluated** (no short-circuit) — complete violation list for the customer.

---

### `RuleEngineReport` (record)

The aggregated result of an engine evaluation.

```java
report.orderId();           // "ORDER-001"
report.overallStatus();     // MET or BREACHED
report.isCompliant();       // true if overallStatus == MET
report.ruleResults();       // List<RuleResult> — one per rule
report.breachedRules();     // only the violated RuleResults
report.allViolations();     // flat list of every RuleViolation across all rules
report.evaluatedAt();       // Instant — when the engine completed evaluation
report.summary();           // formatted multi-line human-readable report
```

---

## 📏 Rule Reference

### `BulkBuyLimitRule`

Enforces a **per-product quantity cap** — no single product may exceed the configured maximum.

```java
new BulkBuyLimitRule()      // default: max 10 per product
new BulkBuyLimitRule(5)     // custom: max 5 per product
```

**Algorithm:**
1. Iterate every line item.
2. If `item.quantity > maxQuantityPerProduct` → record a `RuleViolation`.
3. Collect all violations (no early exit).

**Priority:** `10` (evaluated first — broad catch-all)

**Example:**
```
productId=1, qty=3  → OK  (3 ≤ 10)
productId=2, qty=8  → OK  (8 ≤ 10)
productId=3, qty=11 → BREACHED  (11 > 10)
```

---

### `BulkBuyLimitCategoryRule`

Enforces a **per-category aggregate quantity cap** — the sum of all items in a restricted category may not exceed the configured limit. This handles cases where a customer spreads units across multiple product lines in the same category.

```java
// Single category:
new BulkBuyLimitCategoryRule("Paracetamol", 5)

// Multiple categories in one rule instance:
new BulkBuyLimitCategoryRule(Map.of(
    "Paracetamol", 5,
    "analgesic",   3
))
```

**Algorithm:**
1. Build a `category → totalQuantity` map from the basket.
2. For each restricted category, sum all item quantities.
3. If aggregate > limit → record a `RuleViolation`.

**Category matching is case-insensitive** — `"Paracetamol"`, `"PARACETAMOL"`, and `"paracetamol"` all match.

**Priority:** `20` (evaluated after `BulkBuyLimitRule`)

**Example:**
```
productId=1, category=Paracetamol, qty=3
productId=4, category=Paracetamol, qty=2
→ Aggregate Paracetamol = 5  ≤ 5  → MET ✓

productId=1, category=Paracetamol, qty=4
productId=4, category=Paracetamol, qty=3
→ Aggregate Paracetamol = 7  > 5  → BREACHED ✗
```

---

## ⚙️ How the Engine Works

```
1. CLIENT calls engine.evaluate(shoppingCart)
          │
2. Engine creates RuleContext(cart)
          │
3. Rules are evaluated in priority order (lowest first)
   ┌──────────────────────────────────────────┐
   │  BulkBuyLimitRule (priority=10)          │
   │    → checks each item individually       │
   │    → returns Compliant or Violated       │
   └──────────────────────────────────────────┘
   ┌──────────────────────────────────────────┐
   │  BulkBuyLimitCategoryRule (priority=20)  │
   │    → aggregates qty by category          │
   │    → returns Compliant or Violated       │
   └──────────────────────────────────────────┘
          │ (ALL rules run — no short-circuit)
          │
4. Engine collects List<RuleResult>
          │
5. RuleEngineReport is created
          │
6. report.overallStatus()
   → BREACHED if any rule is Violated
   → MET if all rules are Compliant
```

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+     |
| Maven | 3.8+   |

### Clone & Build

```bash
git clone <repository-url>
cd shopping-cart-rule-service
mvn clean compile
```

---

## 🎬 Running the Demo

The demo app runs **5 scenarios** covering all rule combinations:

```bash
mvn compile exec:java -Dexec.mainClass="com.tesco.restriction.RestrictionRuleEngineApp"
```

### Demo Scenarios

| Scenario | Cart | Expected |
|----------|------|----------|
| **A** — Requirements example | Paracetamol(3+2) + analgesic(3) + chocolate(8) | `MET` |
| **B** — BulkBuyLimit breach | soda(12) — exceeds 10/product | `BREACHED` |
| **C** — Category breach | Paracetamol(4+3) — total 7 > 5 | `BREACHED` |
| **D** — Both rules breach | Paracetamol(6) + soda(11) | `BREACHED` |
| **E** — Multi-category engine | analgesic(4) — exceeds custom limit of 3 | `BREACHED` |

### Sample Output (Scenario A)

```
────────────────────────────────────────────────────────────
  SCENARIO A — Requirements example (expected: MET)
────────────────────────────────────────────────────────────
  Cart: ORDER-001
    • productId=1     category=Paracetamol      qty=3
    • productId=2     category=analgesic        qty=3
    • productId=3     category=chocolate        qty=8
    • productId=4     category=Paracetamol      qty=2

========================================
  RESTRICTION RULE ENGINE REPORT
========================================
  Order ID       : ORDER-001
  Overall Status : MET
  Rules Evaluated: 2
----------------------------------------
  ► Rule: BulkBuyLimit  [MET]
    ✓ No violations found.
  ► Rule: BulkBuyLimitCategory  [MET]
    ✓ No violations found.
========================================
  RESTRICTION STATUS → MET
```

---

## 🧪 Running Tests

```bash
# Run all 42 tests
mvn test

# Run a specific test class
mvn test -Dtest=BulkBuyLimitRuleTest
mvn test -Dtest=BulkBuyLimitCategoryRuleTest
mvn test -Dtest=RuleEngineTest
```

### Test Coverage Summary

| Test Class | Tests | What Is Covered |
|---|---|---|
| `BulkBuyLimitRuleTest` | 18 | MET/BREACHED scenarios, parameterised qty boundaries, custom limits, validation |
| `BulkBuyLimitCategoryRuleTest` | 14 | Case-insensitive matching, multi-category, aggregate logic, constructor validation |
| `RuleEngineTest` | 10 | Full Tesco scenario, priority ordering, full-scan (non-short-circuit), builder, custom rule extension |
| **Total** | **42** | **All passing ✅** |

---

## 🔌 Adding a New Rule

The engine is built on the **Open/Closed Principle** — add a rule by implementing `RestrictionRule`. Zero changes to the engine or any existing rule.

### Example: Minimum Age Rule

```java
package com.tesco.restriction.rule.impl;

import com.tesco.restriction.model.RuleViolation;
import com.tesco.restriction.rule.RestrictionRule;
import com.tesco.restriction.rule.RuleContext;
import com.tesco.restriction.rule.RuleResult;

import java.util.List;
import java.util.Set;

public final class MinAgeLimitRule implements RestrictionRule {

    private final int minimumAge;
    private final Set<String> ageRestrictedCategories;

    public MinAgeLimitRule(int minimumAge, Set<String> categories) {
        this.minimumAge = minimumAge;
        this.ageRestrictedCategories = Set.copyOf(categories);
    }

    @Override public String getName() { return "MinAgeLimit"; }

    @Override
    public String getDescription() {
        return "Customer must be at least %d to purchase: %s".formatted(minimumAge, ageRestrictedCategories);
    }

    @Override
    public int priority() { return 30; }

    @Override
    public RuleResult evaluate(RuleContext context) {
        int customerAge = context.getMetadata("customerAge", Integer.class, Integer.MAX_VALUE);

        boolean hasRestrictedItem = context.cart().items().stream()
            .anyMatch(i -> ageRestrictedCategories.contains(i.categoryLower()));

        if (hasRestrictedItem && customerAge < minimumAge) {
            return RuleResult.violated(getName(),
                RuleViolation.forCategory("age-restricted", customerAge, minimumAge));
        }
        return RuleResult.compliant(getName());
    }
}
```

### Wire It In

```java
RuleEngine engine = RuleEngine.builder()
    .withRule(new BulkBuyLimitRule(10))
    .withRule(new BulkBuyLimitCategoryRule("Paracetamol", 5))
    .withRule(new MinAgeLimitRule(18, Set.of("alcohol", "tobacco")))  // ← just add it
    .build();
```

---

## 🎨 Design Patterns

| Pattern | Where | Benefit |
|---------|-------|---------|
| **Strategy** | `RestrictionRule` interface + implementations | Each rule is a swappable, independent strategy |
| **Builder** | `RuleEngine.Builder` | Fluent, readable engine construction; validates at `build()` |
| **Template Method** | `RestrictionRule.priority()` default | Rules inherit sensible defaults, override only when needed |
| **Value Object** | All records | Immutable, equality by value, safe to cache/share |
| **Sealed Type / ADT** | `RuleResult` | Exhaustive pattern matching at the compiler level |

---

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 21 | Language (records, sealed interfaces, pattern matching) |
| **Maven** | 3.8+ | Build & dependency management |
| **SLF4J + Logback** | 2.0.9 / 1.4.14 | Structured logging in the engine |
| **Jackson** | 2.16.0 | JSON support (ready for config-driven rule loading) |
| **JUnit Jupiter** | 5.10.1 | Unit & integration testing |
| **AssertJ** | 3.24.2 | Fluent, readable test assertions |

---

## 🗺️ Future Enhancement Ideas

| Enhancement | Notes |
|-------------|-------|
| **Config-driven rules** | Load `categoryLimits` from a JSON/YAML config file using Jackson — no code changes needed for limit adjustments |
| **Rule metadata** | Attach regulatory reference codes (e.g. UK legislation IDs) to each rule for audit trails |
| **Fail-fast mode** | Add an `EvaluationStrategy` to the engine — short-circuit on first breach for performance-sensitive paths |
| **Age-gating rule** | Use `RuleContext.metadata` to pass `customerAge` from the session and restrict age-gated categories |
| **Store-type rules** | Some products may have different limits in-store vs online — metadata key `storeType` already supported |
| **Rule versioning** | Tag rules with a `version()` field to track regulatory changes over time |
| **Metrics** | Wrap `RuleEngine.evaluate()` with Micrometer to emit per-rule latency and breach-rate counters |
| **Spring Boot integration** | Wrap as a `@Service` bean; inject rules as `List<RestrictionRule>` via Spring component scan |

---

*🌀 Magic applied with [Wibey VS Code Extension](https://wibey.walmart.com/code) 🪄*
