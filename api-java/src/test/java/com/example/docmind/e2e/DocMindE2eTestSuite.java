package com.example.docmind.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * =============================================================================
 * DocMind Master E2E Test Suite (Tiers 1 - 4)
 * =============================================================================
 * Target runner command: .\mvnw.cmd test -Dtest=DocMindE2eTestSuite
 *
 * Total tests: 170 tests
 * - Tier 1: Feature Coverage (80 tests covering all 12 core features & 5 domains)
 * - Tier 2: Boundary & Corner Cases (60 tests, >= 5 per feature)
 * - Tier 3: Cross-Feature Combinations (20 tests, pairwise & multi-feature)
 * - Tier 4: Real-World Application Scenarios (10 full-lifecycle customer journeys)
 * =============================================================================
 */
@DisplayName("DocMind Master E2E Test Suite (Tiers 1 - 4, 170 tests)")
public class DocMindE2eTestSuite {

    @Nested
    @DisplayName("Tier 1 - Feature Coverage (80 tests)")
    public class Tier1 extends Tier1FeatureCoverageE2eTest {}

    @Nested
    @DisplayName("Tier 2 - Boundary & Corner Cases (60 tests)")
    public class Tier2 extends Tier2BoundaryCornerE2eTest {}

    @Nested
    @DisplayName("Tier 3 - Cross-Feature Combinations (20 tests)")
    public class Tier3 extends Tier3CrossFeatureE2eTest {}

    @Nested
    @DisplayName("Tier 4 - Real-World Application Scenarios (10 tests)")
    public class Tier4 extends Tier4RealWorldScenariosE2eTest {}
}
