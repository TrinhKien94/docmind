# E2E Test Infra: DocMind Responsive Mobile UI & Messenger Integration

## Test Philosophy
- Requirement-driven, opaque-box, multi-tier testing.
- Methodology: Category-Partition + Boundary Value Analysis + Pairwise Combinatorial + Real-World Workload Testing.

## Feature Inventory
| # | Feature | Source | Tier 1 | Tier 2 | Tier 3 |
|---|---------|--------|:------:|:------:|:------:|
| 1 | R1. Compact Responsive Header | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 2 | R2. Viewport & Scroll Containment | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 3 | R3. Full-Screen Mobile Chat & Drawer | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 4 | R4. EvoAgentX Self-Evolution UI | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |
| 5 | R5. Messenger Integration & Webhook | ORIGINAL_REQUEST §R5 | 5 | 5 | ✓ |
| 6 | R6. Automated Build & Live Delivery | ORIGINAL_REQUEST §Acceptance | 5 | 5 | ✓ |

## Test Architecture
- **Backend Test Runner**: `.\mvnw.cmd test` in `D:\docmind\api-java` (Spring Boot tests).
- **Python Test Runner**: `pytest` in `D:\docmind\doc-service-py`.
- **Frontend Build Runner**: `$env:PATH = "C:\Users\kieIC\AppData\Roaming\npm;$env:PATH"; npm run build` in `D:\docmind\frontend`.
- **Automated Viewport & Scroll Harness**: `python D:\docmind\.agents\teamwork\explorer_survey_3\verify_viewports.py` driving headless Chrome via Playwright:
  - Mobile: `375x812` (iPhone X / Pixel 9 equivalent)
  - Tablet: `768x1024` (iPad)
  - Desktop: `1280x800` (MacBook / Laptop)
  - Checks: `scrollWidth <= clientWidth`, full-screen mobile chat, drawer toggle, header/input bounds within viewport height, internal scrolling on messages-area and modals.

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Mobile Customer Consultation via Tailscale | Mobile Viewport, Drawer, Chat Input, EvoAgentX Feedback | High |
| 2 | Facebook Messenger Customer Chat & Quick Replies | Webhook receiver, multi-turn PSID, Graph Send API, quick replies | High |
| 3 | Facebook Messenger Reaction/Postback Feedback Evolution | Postback payload, EvoAgentX `recordFeedbackAndEvolve`, prompt adaptation | High |
| 4 | Offline / Demo Simulator Mode in Web UI | Frontend MessengerModal simulator, mock responses, zero external dependency | Medium |
| 5 | Responsive EvoAgentX Inspection on Small Screens | CustomerEvolutionModal, stats grid, learned guidelines, TextGrad logs | Medium |

## Coverage Thresholds
- Tier 1: >=5 per feature
- Tier 2: >=5 per feature (where boundaries exist)
- Tier 3: pairwise coverage of major feature interactions
- Tier 4: >=5 realistic application scenarios
