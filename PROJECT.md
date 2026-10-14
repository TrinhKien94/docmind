# Project: DocMind Responsive Mobile UI, EvoAgentX Styling & Facebook Messenger Integration

## Architecture
DocMind is a multi-service intelligent document and customer consultation platform:
- **Frontend** (`D:\docmind\frontend`): Vue 3 + Vite 8 SPA styled with custom dark glassmorphism design tokens (`src/style.css`).
- **Backend API** (`D:\docmind\api-java`): Spring Boot 3 Java service providing RAG indexing, customer consultation (`CustomerChatService`), EvoAgentX self-evolution (`CustomerSelfEvolutionService`), and Facebook Messenger webhook and bot services.
- **Python Doc Service** (`D:\docmind\doc-service-py`): FastAPI document chunking and vector embedding service.
- **Proxy & Live Server**: Vite server on `0.0.0.0:3000` proxied via Tailscale on `http://100.101.62.94:80`.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | R1. Compact Responsive Header | Redesign top header bar in `ChatArea.vue` across mobile (320-767px), tablet (768-1023px), desktop (>=1024px) with drawer toggle, mode badge, and collapsible settings | M2 | ORIGINAL_REQUEST §R1 |
| 2 | R2. Viewport & Scroll Containment | Fix flex hierarchy with `100dvh`, `min-height: 0`, `-webkit-overflow-scrolling: touch`, `overscroll-behavior: contain` to prevent clipping behind address bars | M2 | ORIGINAL_REQUEST §R2 |
| 3 | R3. Full-Screen Mobile Chat & Drawer | Keep desktop side-by-side (>=768px); on mobile (<768px) make chat full-screen and `DocumentSidebar.vue` an off-canvas drawer with backdrop and >=44px tap targets | M2 | ORIGINAL_REQUEST §R3 |
| 4 | R4. EvoAgentX Self-Evolution UI | Build `CustomerEvolutionModal.vue` adhering strictly to dark glassmorphism tokens (metrics cards, guidelines, MIPRO exemplars, gradients log, scenario trigger) | M3 | ORIGINAL_REQUEST §R4 |
| 5 | R5.1 Messenger Properties & Config | Externalize `docmind.messenger.*` configuration in `MessengerProperties.java` and `application.properties` | M1 | ORIGINAL_REQUEST §R5 |
| 6 | R5.2 Messenger Webhook Verification | Implement `GET /api/messenger/webhook` verifying `hub.mode=subscribe`, `hub.verify_token`, returning `hub.challenge` | M1 | ORIGINAL_REQUEST §R5 |
| 7 | R5.3 Messenger Webhook Event Receiver | Implement `POST /api/messenger/webhook` with `X-Hub-Signature-256`, PSID session store, `CustomerChatService` routing, Graph Send API dispatch, and quick replies | M1 | ORIGINAL_REQUEST §R5 |
| 8 | R5.4 Messenger Feedback Evolution | Map Messenger reactions/postback payloads (`FEEDBACK_HELPFUL_*`, `FEEDBACK_UNHELPFUL_*`) to `CustomerSelfEvolutionService.recordFeedbackAndEvolve` | M1 | ORIGINAL_REQUEST §R5 |
| 9 | R5.5 Messenger Simulator & Status API | Implement `GET /api/messenger/status`, `POST /api/messenger/simulate`, `DELETE /api/messenger/sessions` | M1 | ORIGINAL_REQUEST §R5 |
| 10 | R5.6 Messenger Backend Tests | Implement `MessengerWebhookControllerTest` and `MessengerServiceTest` ensuring 100% pass | M1 | ORIGINAL_REQUEST §R5 |
| 11 | R5.7 Messenger Frontend UI | Build `MessengerModal.vue` and header trigger button in `ChatArea.vue` with status, simulator, and session management | M3 | ORIGINAL_REQUEST §R5 |
| 12 | R5.8 Frontend API & Mock Fallbacks | Integrate Messenger API calls in `frontend/src/api.js` and rich offline demo mocks in `mockData.js` | M3 | ORIGINAL_REQUEST §R5 |
| 13 | R6.1 Frontend Build Verification | Verify `npm run build` with Node 24 produces clean build with 0 errors | M4 | ORIGINAL_REQUEST §Acceptance |
| 14 | R6.2 Backend Test Verification | Verify `.\mvnw.cmd test` (53+ tests) and `pytest` (24+ tests) pass 100% | M4 | ORIGINAL_REQUEST §Acceptance |
| 15 | R6.3 Viewport & Scroll E2E Verification | Programmatic automated Playwright verification at `375x812` mobile and `1280x800` desktop | M4 | ORIGINAL_REQUEST §Acceptance |
| 16 | R6.4 Git Commit & PR Delivery | Commit changes to `feat/codex-9router-customer-chat-api` and push to remote `fork`, updating PR #1 | M4 | ORIGINAL_REQUEST §Acceptance |
| 17 | R6.5 Live Server Verification | Verify Vite dev server (`0.0.0.0:3000`) and Tailscale proxy (`http://100.101.62.94`) serve HTTP 200 OK | M4 | ORIGINAL_REQUEST §Acceptance |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Backend Messenger & EvoAgentX Bridge | Implement `MessengerProperties`, DTOs, `FacebookGraphClient`, `MessengerService`, `MessengerWebhookController`, tests in `api-java` | none | DONE |
| 2 | M2: Responsive Mobile Chat & Sidebar Drawer | Implement `style.css` dynamic viewport tokens, `App.vue` drawer state, `DocumentSidebar.vue` mobile drawer, `ChatArea.vue` responsive header & scrolling | none | DONE |
| 3 | M3: EvoAgentX Styling & Messenger Frontend UI | Implement `CustomerEvolutionModal.vue`, `MessengerModal.vue`, `ChatArea.vue` trigger buttons, `api.js` & `mockData.js` | M1, M2 | DONE |
| 4 | M4: Final Milestone (E2E Verification, Hardening & PR Push) | Full test suite execution, Playwright programmatic viewport audits, adversarial stress testing, git commit & PR push to fork | M1, M2, M3 | DONE |

## Interface Contracts

### Backend ↔ Frontend Messenger API Contract
- `GET /api/messenger/status` -> `MessengerStatusResponse`:
  `{ "enabled": boolean, "verify_token_configured": boolean, "page_token_configured": boolean, "app_secret_configured": boolean, "verify_signature": boolean, "active_sessions_count": int, "webhook_url": string, "graph_base_url": string, "api_version": string, "recent_interactions": List<MessengerInteractionDto> }`
- `POST /api/messenger/simulate` -> Request: `{ "sender_id": string, "message": string, "scenario": string }` -> Response: `{ "sender_id": string, "received_message": string, "scenario": string, "reply_sent": string, "quick_replies": List<string>, "interaction_id": string, "simulated": true, "timestamp": string }`
- `DELETE /api/messenger/sessions` -> `{ "message": "Messenger sessions cleared", "cleared_count": int }`
- `GET /api/messenger/webhook?hub.mode=subscribe&hub.verify_token={token}&hub.challenge={challenge}` -> returns `hub.challenge` as plain text (HTTP 200) or HTTP 403.
- `POST /api/messenger/webhook` -> handles JSON payload with `object: "page"`, returns HTTP 200 `EVENT_RECEIVED`.

### Frontend Component Contracts
- `App.vue` controls:
  - `isMobileSidebarOpen` (boolean) passed to `DocumentSidebar.vue` (`isOpen` prop, `@close` event) and toggled from `ChatArea.vue` (`@toggle-sidebar` event).
  - `isEvolutionModalOpen` (boolean) toggled from `ChatArea.vue` (`@open-evolution` event) to show `CustomerEvolutionModal.vue`.
  - `isMessengerModalOpen` (boolean) toggled from `ChatArea.vue` (`@open-messenger` event) to show `MessengerModal.vue`.

## Code Layout
- `D:\docmind\api-java\src\main\java\com\example\docmind\config\MessengerProperties.java`
- `D:\docmind\api-java\src\main\java\com\example\docmind\dto\messenger\*`
- `D:\docmind\api-java\src\main\java\com\example\docmind\service\FacebookGraphClient.java`
- `D:\docmind\api-java\src\main\java\com\example\docmind\service\MessengerService.java`
- `D:\docmind\api-java\src\main\java\com\example\docmind\controller\MessengerWebhookController.java`
- `D:\docmind\api-java\src\test\java\com\example\docmind\controller\MessengerWebhookControllerTest.java`
- `D:\docmind\api-java\src\test\java\com\example\docmind\service\MessengerServiceTest.java`
- `D:\docmind\frontend\src\style.css`
- `D:\docmind\frontend\src\App.vue`
- `D:\docmind\frontend\src\components\ChatArea.vue`
- `D:\docmind\frontend\src\components\DocumentSidebar.vue`
- `D:\docmind\frontend\src\components\CustomerEvolutionModal.vue`
- `D:\docmind\frontend\src\components\MessengerModal.vue`
- `D:\docmind\frontend\src\api.js`
- `D:\docmind\frontend\src\mockData.js`
