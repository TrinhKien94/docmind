# E2E Test Infra: DocMind Responsive Mobile UI, Christian Dior KB, EvoAgentX & AstrBot Multi-Platform Hub

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
| 5 | R5. AstrBot Multi-Platform Hub (19 Adapters incl. Facebook Messenger) | ORIGINAL_REQUEST §R5 | 5 | 5 | ✓ |
| 6 | R6. Automated Build & Live Delivery | ORIGINAL_REQUEST §Acceptance | 5 | 5 | ✓ |

## Test Architecture
- **Backend Test Runner**: `.\mvnw.cmd test` in `D:\docmind\api-java` (Spring Boot tests including `AstrBotServiceTest` & `AstrBotControllerTest`).
- **Python Test Runner**: `pytest` in `D:\docmind\doc-service-py`.
- **Frontend Build Runner**: `npm run build` in `D:\docmind\frontend`.
- **Automated Playwright E2E Harnesses**:
  - `python frontend/tests/test_astrbot_modal_e2e.py` (6-tab AstrBot Control Center, 19 platform catalog, QR provisioning, Live Simulator, UMO Sessions, Personas/Providers/Cron, Plugins/Logs/Traces).
  - `python frontend/tests/test_m3_modals_interactive.py` (Desktop `1280x800` & Mobile `375x812` interactive modal audit for `CustomerEvolutionModal` and `AstrBotModal`).
  - `python frontend/tests/test_challenger_m3_stress.py` (Cross-breakpoint resize, overflow, touch targets, z-index dominance).

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Mobile Customer Consultation via Tailscale | Mobile Viewport, Drawer, Chat Input, Christian Dior KB, EvoAgentX Feedback | High |
| 2 | AstrBot Unified Webhook for Facebook Messenger (`messenger_dior_page`) | Meta `hub.challenge` verification, `X-Hub-Signature-256` HMAC-SHA256 validation, multi-turn PSID UMO routing, `<= 20` char quick replies | High |
| 3 | AstrBot Messenger Reaction & Postback Feedback -> EvoAgentX Evolution | Meta reaction (`👍`/`👎`) & `FEEDBACK_HELPFUL_*`/`FEEDBACK_UNHELPFUL_*` postbacks triggering `CustomerSelfEvolutionService.recordFeedbackAndEvolve` | High |
| 4 | Multi-Platform Live Simulator & Star Slash Commands in Web UI | `AstrBotModal.vue` simulator across Telegram, Messenger, Discord, WeChat (`weixin_oc` QR) & `/help`, `/status`, `/persona`, `/evolve` commands | High |
| 5 | Responsive EvoAgentX Inspection on Small Screens | `CustomerEvolutionModal.vue`, stats grid, learned guidelines, MIPRO exemplars, TextGrad logs | Medium |

## Coverage Thresholds
- Tier 1: >=5 per feature
- Tier 2: >=5 per feature (where boundaries exist)
- Tier 3: pairwise coverage of major feature interactions
- Tier 4: >=5 realistic application scenarios
