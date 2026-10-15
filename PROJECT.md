# Project: DocMind Responsive Mobile UI, Christian Dior Luxury KB, EvoAgentX Self-Evolution & AstrBot Multi-Platform Control Center

## Architecture
DocMind is a multi-service intelligent document and luxury customer consultation platform:
- **Frontend** (`D:\docmind\frontend`): Vue 3 + Vite 8 SPA styled with custom dark glassmorphism design tokens (`src/style.css`), featuring `CustomerEvolutionModal.vue` and `AstrBotModal.vue` (6-tab omni-channel management hub).
- **Backend API** (`D:\docmind\api-java`): Spring Boot 4 Java service providing RAG indexing, Christian Dior Luxury Customer Consultation (`CustomerChatService` + `dior-luxury-knowledge-base.md`), EvoAgentX self-evolution (`CustomerSelfEvolutionService`), and the unified **AstrBot Multi-Platform Control Center** (`AstrBotController`, `AstrBotService`, `AstrBotPlatformCatalog` with 19 platform adapters including `facebook_messenger`).
- **Python Doc Service** (`D:\docmind\doc-service-py`): FastAPI document chunking and vector embedding service.
- **Proxy & Live Server**: Vite server on `0.0.0.0:3000` proxied via Tailscale on `http://100.101.62.94:80`.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | R1. Compact Responsive Header | Redesign top header bar in `ChatArea.vue` across mobile (320-767px), tablet (768-1023px), desktop (>=1024px) with drawer toggle, mode badge, EvoAgentX trigger, AstrBot Hub trigger, and collapsible settings | M2 | ORIGINAL_REQUEST §R1 |
| 2 | R2. Viewport & Scroll Containment | Fix flex hierarchy with `100dvh`, `min-height: 0`, `-webkit-overflow-scrolling: touch`, `overscroll-behavior: contain` to prevent clipping behind address bars | M2 | ORIGINAL_REQUEST §R2 |
| 3 | R3. Full-Screen Mobile Chat & Drawer | Keep desktop side-by-side (>=768px); on mobile (<768px) make chat full-screen and `DocumentSidebar.vue` an off-canvas drawer with backdrop and >=44px tap targets | M2 | ORIGINAL_REQUEST §R3 |
| 4 | R4. EvoAgentX Self-Evolution UI | Build `CustomerEvolutionModal.vue` adhering strictly to dark glassmorphism tokens (metrics cards, guidelines, MIPRO exemplars, gradients log, scenario trigger) | M3 | ORIGINAL_REQUEST §R4 |
| 5 | R5.1 AstrBot 19-Platform Catalog & Config | Externalize `docmind.astrbot.*` configuration in `AstrBotProperties.java`, `AstrBotPlatformCatalog.java` (19 official platform adapters including `facebook_messenger`, `telegram`, `discord`, `slack`, `lark`, `line`, `wecom`, `weixin_oc`, `qq_official`, `aiocqhttp`, `satori`, `kook`, `mattermost`, `misskey`, `webchat`), and `application.properties` | M1 | ORIGINAL_REQUEST §R5 |
| 6 | R5.2 Unified Webhook Verification & Signature Security | Implement `GET|POST /api/astrbot/webhooks/platforms/{webhookUuid}` supporting Meta `hub.mode=subscribe` / `hub.verify_token` / `hub.challenge` verification and constant-time `X-Hub-Signature-256` HMAC-SHA256 validation | M1 | ORIGINAL_REQUEST §R5 |
| 7 | R5.3 Multi-Platform Event Routing & UMO Sessions | Route incoming events (including Meta `object: "page"` `entry[].messaging[]`, delivery/read/echo filtering, and `<= 20` char quick replies) through AstrBot UMO (`{platform_id}:{message_type}:{session_id}`) to `CustomerChatService` + Christian Dior KB | M1 | ORIGINAL_REQUEST §R5 |
| 8 | R5.4 AstrBot Feedback -> EvoAgentX Bridge | Map platform reactions (`👍`, `❤️`, `👎`, `😠`) and postback/quick-reply payloads (`FEEDBACK_HELPFUL_*`, `FEEDBACK_UNHELPFUL_*`) directly to `CustomerSelfEvolutionService.recordFeedbackAndEvolve` | M1 | ORIGINAL_REQUEST §R5 |
| 9 | R5.5 AstrBot Simulator, Star Slash Commands & Control API | Implement `/api/astrbot/overview`, `/api/astrbot/bots/**`, `/api/astrbot/simulate` (supporting `/help`, `/status`, `/reset`, `/persona`, `/scenario`, `/evolve`, `/whoami`), `/api/astrbot/sessions`, `/routes`, `/personas`, `/providers`, `/cron`, `/extensions`, `/logs`, `/traces` | M1 | ORIGINAL_REQUEST §R5 |
| 10 | R5.6 AstrBot Backend Tests | Implement `AstrBotControllerTest` and `AstrBotServiceTest` covering all 19 adapters, Meta Messenger webhook signature/challenge/reactions/postbacks, UMO routing, and Star commands | M1 | ORIGINAL_REQUEST §R5 |
| 11 | R5.7 AstrBot Frontend Control Center UI | Build `AstrBotModal.vue` (6-tab omni-channel dashboard) and header trigger button `.btn-astrbot` in `ChatArea.vue` (replacing redundant standalone Messenger modal/button) | M3 | ORIGINAL_REQUEST §R5 |
| 12 | R5.8 Christian Dior Luxury KB & Offline Mocks | Seed comprehensive Christian Dior VN 2026 knowledge base (`dior-luxury-knowledge-base.md`) and rich offline demo fallbacks in `frontend/src/api.js` and `mockData.js` | M3 | ORIGINAL_REQUEST §R5 |
| 13 | R6.1 Frontend Build Verification | Verify `npm run build` produces clean production bundle with 0 errors | M4 | ORIGINAL_REQUEST §Acceptance |
| 14 | R6.2 Backend Test Verification | Verify `.\mvnw.cmd test` and `pytest` pass 100% | M4 | ORIGINAL_REQUEST §Acceptance |
| 15 | R6.3 Viewport & Scroll E2E Verification | Programmatic automated Playwright verification across mobile (`375x812`), tablet (`768x1024`), and desktop (`1280x800`, `1440x900`) | M4 | ORIGINAL_REQUEST §Acceptance |
| 16 | R6.4 Git Commit & PR #1 Summary Sync | Commit unified AstrBot architecture to `feat/codex-9router-customer-chat-api`, push to `fork`, and update GitHub PR #1 Title & Summary | M4 | ORIGINAL_REQUEST §Acceptance |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Unified AstrBot Multi-Platform Hub & EvoAgentX Bridge | Consolidate Facebook Messenger & 19 chat adapters into `AstrBotPlatformCatalog`, `AstrBotService`, `AstrBotController`, DTOs, and unit/integration tests in `api-java` | none | DONE |
| 2 | M2: Responsive Mobile Chat & Sidebar Drawer | Implement `style.css` dynamic viewport tokens, `App.vue` drawer state, `DocumentSidebar.vue` mobile drawer, `ChatArea.vue` responsive header & scrolling | none | DONE |
| 3 | M3: EvoAgentX Styling, Christian Dior KB & AstrBot Frontend UI | Implement `CustomerEvolutionModal.vue`, `AstrBotModal.vue`, `ChatArea.vue` trigger buttons, `dior-luxury-knowledge-base.md`, `api.js` & `mockData.js` | M1, M2 | DONE |
| 4 | M4: Final Milestone (E2E Verification, Consolidation & PR #1 Update) | Remove redundant standalone Messenger files, run full verification suite, push to `fork`, and update GitHub PR #1 Title & Summary | M1, M2, M3 | DONE |

## Interface Contracts

### Backend ↔ Frontend AstrBot Multi-Platform Control Center API Contract
- `GET /api/astrbot/overview` -> `AstrBotOverviewResponse`:
  `{ "enabled": boolean, "version": string, "timezone": string, "webhook_base_path": string, "stats": Map, "bot_types": List<AstrBotPlatformTypeDto> (19 adapters), "bots": List<AstrBotInstanceDto>, "sessions": List<AstrBotSessionRuleDto>, "personas": List<AstrBotPersonaDto>, "providers": List<AstrBotProviderDto>, "cron_jobs": List<AstrBotCronJobDto>, "extensions": List<AstrBotExtensionDto>, "traces": List<AstrBotTraceDto>, "logs": List<AstrBotLogEntryDto> }`
- `POST /api/astrbot/bots`, `PUT /api/astrbot/bots/{botId}`, `PATCH|POST /api/astrbot/bots/{botId}/enabled`, `DELETE /api/astrbot/bots/{botId}`, `POST /api/astrbot/bots/{botId}/test`, `POST /api/astrbot/bot-types/{botType}/registration`
- `GET /api/astrbot/webhooks/platforms/{webhookUuid}` -> Unified Webhook verification endpoint (validates Meta Messenger `hub.mode=subscribe` & `hub.verify_token` against adapter config, returning `hub.challenge` HTTP 200 or HTTP 403 Forbidden).
- `POST /api/astrbot/webhooks/platforms/{webhookUuid}` -> Unified Webhook event receiver with constant-time `X-Hub-Signature-256` HMAC-SHA256 verification, Meta `object: "page"` batch `entry[].messaging[]` handling, reaction/postback EvoAgentX feedback evolution, and `<= 20` char Messenger quick replies.
- `POST /api/astrbot/simulate` -> Multi-platform & Star Slash Command (`/help`, `/status`, `/reset`, `/persona`, `/scenario`, `/evolve`, `/whoami`) live simulator routed through `CustomerChatService` + Dior KB + EvoAgentX.
- `GET /api/astrbot/sessions`, `POST /api/astrbot/sessions/rules`, `DELETE /api/astrbot/sessions`, `GET|POST|DELETE /api/astrbot/routes` -> UMO (`{platform_id}:{message_type}:{session_id}`) session & route management.
- `GET|POST|PUT|DELETE /api/astrbot/personas`, `GET|POST|PUT|DELETE /api/astrbot/providers`, `GET|POST|PUT|DELETE /api/astrbot/cron`, `POST /api/astrbot/cron/{jobId}/run`, `GET|POST|PATCH|DELETE /api/astrbot/extensions`, `GET|DELETE /api/astrbot/logs`, `GET /api/astrbot/traces`.

### Frontend Component Contracts
- `App.vue` controls:
  - `isMobileSidebarOpen` (boolean) passed to `DocumentSidebar.vue` (`isOpen` prop, `@close` event) and toggled from `ChatArea.vue` (`@toggle-sidebar` event).
  - `isEvolutionModalOpen` (boolean) toggled from `ChatArea.vue` (`@open-evolution` event) to show `CustomerEvolutionModal.vue`.
  - `isAstrBotModalOpen` (boolean) toggled from `ChatArea.vue` (`@open-astrbot` event) to show `AstrBotModal.vue` (6-tab omni-channel management dashboard including Facebook Messenger adapter `messenger_dior_page`).

## Code Layout
- `D:\docmind\api-java\src\main\java\com\example\docmind\config\AstrBotProperties.java`
- `D:\docmind\api-java\src\main\java\com\example\docmind\dto\astrbot\*`
- `D:\docmind\api-java\src\main\java\com\example\docmind\service\astrbot\AstrBotPlatformCatalog.java`
- `D:\docmind\api-java\src\main\java\com\example\docmind\service\astrbot\AstrBotService.java`
- `D:\docmind\api-java\src\main\java\com\example\docmind\controller\AstrBotController.java`
- `D:\docmind\api-java\src\main\resources\knowledge\dior-luxury-knowledge-base.md`
- `D:\docmind\api-java\src\test\java\com\example\docmind\controller\AstrBotControllerTest.java`
- `D:\docmind\api-java\src\test\java\com\example\docmind\service\AstrBotServiceTest.java`
- `D:\docmind\frontend\src\style.css`
- `D:\docmind\frontend\src\App.vue`
- `D:\docmind\frontend\src\components\ChatArea.vue`
- `D:\docmind\frontend\src\components\DocumentSidebar.vue`
- `D:\docmind\frontend\src\components\CustomerEvolutionModal.vue`
- `D:\docmind\frontend\src\components\AstrBotModal.vue`
- `D:\docmind\frontend\src\api.js`
- `D:\docmind\frontend\src\mockData.js`
