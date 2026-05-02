package com.tesco.restriction;

import com.tesco.restriction.engine.RuleEngine;
import com.tesco.restriction.engine.RuleEngineReport;
import com.tesco.restriction.model.CartItem;
import com.tesco.restriction.model.RestrictionStatus;
import com.tesco.restriction.model.ShoppingCart;
import com.tesco.restriction.rule.impl.BulkBuyLimitCategoryRule;
import com.tesco.restriction.rule.impl.BulkBuyLimitRule;

import java.util.List;
import java.util.Map;

/**
 * Executable demo that exercises the Restriction Rule Engine with the
 * canonical Tesco scenario described in the business requirements.
 *
 * <p>Run with: {@code mvn exec:java -Dexec.mainClass="com.tesco.restriction.RestrictionRuleEngineApp"}
 */
public class RestrictionRuleEngineApp {

    public static void main(String[] args) {

        // -----------------------------------------------------------------------
        // 1. Build the Rule Engine with Tesco business rules
        // -----------------------------------------------------------------------
        RuleEngine engine = RuleEngine.builder()
                .withRule(new BulkBuyLimitRule(10))                         // BulkBuyLimit        : max 10 per product
                .withRule(new BulkBuyLimitCategoryRule("Paracetamol", 5))   // BulkBuyLimitCategory: max 5 for Paracetamol
                .build();

        // -----------------------------------------------------------------------
        // 2. Scenario A – The example from the requirements (expected: MET)
        //    Item-1: productId=1, category=Paracetamol, quantity=3
        //    Item-2: productId=2, category=analgesic,   quantity=3
        //    Item-3: productId=3, category=chocolate,   quantity=8
        //    Item-4: productId=4, category=Paracetamol, quantity=2
        //
        //    BulkBuyLimit        → all products ≤ 10  → MET
        //    BulkBuyLimitCategory → Paracetamol total = 3+2 = 5 ≤ 5 → MET
        // -----------------------------------------------------------------------
        ShoppingCart scenarioACart = new ShoppingCart(
                "ORDER-001",
                List.of(
                        new CartItem("1", "Paracetamol", 3),
                        new CartItem("2", "analgesic",   3),
                        new CartItem("3", "chocolate",   8),
                        new CartItem("4", "Paracetamol", 2)
                )
        );

        runAndPrint("SCENARIO A — Requirements example (expected: MET)", engine, scenarioACart);

        // -----------------------------------------------------------------------
        // 3. Scenario B – BulkBuyLimit BREACHED (product qty > 10)
        //    Item-1: productId=5, category=soda, quantity=12
        // -----------------------------------------------------------------------
        ShoppingCart scenarioBCart = new ShoppingCart(
                "ORDER-002",
                List.of(
                        new CartItem("5", "soda",         12),   // 12 > 10 → BREACHED
                        new CartItem("6", "crisps",        4)
                )
        );

        runAndPrint("SCENARIO B — BulkBuyLimit breached (expected: BREACHED)", engine, scenarioBCart);

        // -----------------------------------------------------------------------
        // 4. Scenario C – BulkBuyLimitCategory BREACHED (Paracetamol total > 5)
        //    Item-1: productId=7, category=Paracetamol, quantity=4
        //    Item-2: productId=8, category=Paracetamol, quantity=3   → total=7 > 5
        // -----------------------------------------------------------------------
        ShoppingCart scenarioCCart = new ShoppingCart(
                "ORDER-003",
                List.of(
                        new CartItem("7", "Paracetamol", 4),
                        new CartItem("8", "Paracetamol", 3),   // aggregate 7 > 5 → BREACHED
                        new CartItem("9", "chocolate",   2)
                )
        );

        runAndPrint("SCENARIO C — BulkBuyLimitCategory breached (expected: BREACHED)", engine, scenarioCCart);

        // -----------------------------------------------------------------------
        // 5. Scenario D – Both rules BREACHED simultaneously
        // -----------------------------------------------------------------------
        ShoppingCart scenarioDCart = new ShoppingCart(
                "ORDER-004",
                List.of(
                        new CartItem("10", "Paracetamol", 6),   // 6 > 5 (category) AND 6 ≤ 10 (product)
                        new CartItem("11", "soda",        11)   // 11 > 10 (product)
                )
        );

        runAndPrint("SCENARIO D — Both rules breached (expected: BREACHED)", engine, scenarioDCart);

        // -----------------------------------------------------------------------
        // 6. Demonstrate the multi-category engine extension
        //    Add a hypothetical rule: max 3 analgesic AND max 5 Paracetamol
        // -----------------------------------------------------------------------
        System.out.println("=".repeat(50));
        System.out.println("  EXTENDED ENGINE — multi-category limits");
        System.out.println("=".repeat(50));

        RuleEngine extendedEngine = RuleEngine.builder()
                .withRule(new BulkBuyLimitRule(10))
                .withRule(new BulkBuyLimitCategoryRule(Map.of(
                        "Paracetamol", 5,
                        "analgesic",   3
                )))
                .build();

        ShoppingCart extendedCart = new ShoppingCart(
                "ORDER-005",
                List.of(
                        new CartItem("1",  "Paracetamol", 3),
                        new CartItem("2",  "analgesic",   4),   // analgesic total 4 > 3 → BREACHED
                        new CartItem("3",  "chocolate",   2)
                )
        );

        runAndPrint("SCENARIO E — Multi-category: analgesic limit breached (expected: BREACHED)",
                extendedEngine, extendedCart);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static void runAndPrint(String label, RuleEngine engine, ShoppingCart cart) {
        System.out.println("\n" + "─".repeat(60));
        System.out.println("  " + label);
        System.out.println("─".repeat(60));
        System.out.println("  Cart: " + cart.orderId());
        cart.items().forEach(item ->
                System.out.printf("    • productId=%-4s  category=%-15s  qty=%d%n",
                        item.productId(), item.category(), item.quantity()));

        RuleEngineReport report = engine.evaluate(cart);

        System.out.println();
        System.out.print(report.summary());

        // Final answer — mirrors the expected output format from the requirements
        RestrictionStatus overallStatus = report.overallStatus();
        System.out.printf("  RESTRICTION STATUS → %s%n%n", overallStatus);
    }
}
