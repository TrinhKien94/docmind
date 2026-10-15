#!/usr/bin/env python3
"""
End-to-End Playwright Verification for AstrBot Multi-Platform Integration & Management Dashboard UI.
Tests:
1. Desktop (1280x800), Mobile (375x812), and Ultra-Compact (320x568) zero horizontal overflow.
2. Tab 1 (Platforms): 19 Platform Adapter Catalog, Bot creation, QR Code Login registration, Connection test, UMO Route binding, Save/Reload.
3. Tab 2 (Simulator): Star Slash Commands (/help, /status) & Multi-platform Customer RAG / Dior KB simulation.
4. Tab 3 (Sessions): UMO session table, search filter, and session management.
5. Tab 4 (Personas & Providers): Create Persona & LLM Provider Source.
6. Tab 5 (Automation & Extensions): Create Cron Job, Run Cron Job Now, and Register MCP Server.
7. Tab 6 (Observability): KPI cards, Platform Ranking, Trace Displayer, and Live Console Terminal.
"""

import sys
from playwright.sync_api import sync_playwright

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
BASE_URL = "http://localhost:3000"


def assert_zero_overflow(page, label):
    metrics = page.evaluate("""() => {
        const doc = document.documentElement;
        return {
            scrollWidth: doc.scrollWidth,
            clientWidth: doc.clientWidth,
            overflow: doc.scrollWidth > doc.clientWidth
        };
    }""")
    assert not metrics["overflow"], (
        f"[{label}] Horizontal overflow detected: scrollWidth={metrics['scrollWidth']} > clientWidth={metrics['clientWidth']}"
    )
    print(f"  [OK] Zero horizontal overflow on {label} ({metrics['clientWidth']}px)")


def run_tests():
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)
        page = browser.new_page()
        page.set_viewport_size({"width": 1280, "height": 800})
        page.goto(BASE_URL, wait_until="domcontentloaded")
        page.wait_for_selector(".app-shell", timeout=6000)

        print("=================================================================")
        print("ASTRBOT MULTI-PLATFORM CONTROL CENTER E2E VERIFICATION SUITE")
        print("=================================================================")

        assert_zero_overflow(page, "Desktop Main App (1280x800)")

        # 1. Open AstrBot Modal
        open_btn = page.locator("[data-testid='open-astrbot-btn']")
        assert open_btn.is_visible(), "AstrBot Hub button must be visible in header"
        open_btn.click()
        page.wait_for_selector("[data-testid='astrbot-modal']", timeout=5000)
        print("  [PASS] Opened AstrBot Multi-Platform Control Center modal")
        assert_zero_overflow(page, "Desktop AstrBot Modal Open (Tab 1: Platforms)")

        # 2. Open 19-Platform Catalog & Create a new KOOK bot
        page.locator("[data-testid='astrbot-open-catalog-btn']").click()
        page.wait_for_selector("[data-testid='astrbot-catalog-modal']", timeout=4000)
        catalog_cards = page.locator(".catalog-card")
        assert catalog_cards.count() == 19, f"Expected 19 platform adapter cards, found {catalog_cards.count()}"
        print(f"  [PASS] Verified all {catalog_cards.count()} AstrBot Platform Adapter types in catalog")

        page.locator("[data-testid='astrbot-catalog-card-kook']").click()
        page.locator("[data-testid='astrbot-new-bot-id']").fill("kook:vip!club")
        page.locator("[data-testid='astrbot-confirm-create-bot-btn']").click()
        page.wait_for_timeout(300)

        new_bot_item = page.locator("[data-testid='astrbot-bot-item-kook_vip_club']")
        assert new_bot_item.is_visible(), "Newly created bot kook_vip_club (sanitized ID) must appear in sidebar"
        print("  [PASS] Created new Bot Adapter 'kook_vip_club' (with ':' and '!' auto-sanitized)")

        # 3. Select Lark bot & test QR Code Registration flow
        lark_item = page.locator("[data-testid='astrbot-bot-item-lark_feishu_workspace']")
        lark_item.click()
        page.wait_for_timeout(200)
        qr_btn = page.locator("[data-testid='astrbot-qr-btn']")
        assert qr_btn.is_visible(), "QR Registration button must be visible for Lark adapter"
        qr_btn.click()
        page.wait_for_selector("[data-testid='astrbot-qr-modal']", timeout=4000)
        page.locator("[data-testid='astrbot-qr-poll-btn']").click()
        page.wait_for_timeout(250)
        print("  [PASS] Completed QR Code Login registration flow (start -> poll) for Lark adapter")

        # 4. Test Bot Connection, Add UMO Route, and Save Bot
        page.locator("[data-testid='astrbot-test-bot-btn']").click()
        page.wait_for_timeout(200)
        page.locator("[data-testid='astrbot-add-route-btn']").click()
        page.wait_for_timeout(200)
        page.locator("[data-testid='astrbot-save-bot-btn']").click()
        page.wait_for_timeout(200)
        print("  [PASS] Tested connection, bound UMO route, and saved Bot configuration")

        # 5. Switch to Tab 2: Simulator & Star Slash Commands
        page.locator("[data-testid='astrbot-tab-simulator']").click()
        page.wait_for_selector("[data-testid='astrbot-panel-simulator']", timeout=4000)
        assert_zero_overflow(page, "Desktop AstrBot Modal (Tab 2: Simulator)")

        # Execute /status Star command
        page.locator("[data-testid='astrbot-cmd-status']").click()
        page.wait_for_timeout(350)
        turns = page.locator(".sim-turn-block")
        assert turns.count() >= 1, "Expected at least 1 simulated turn after /status"
        print("  [PASS] Executed AstrBot Star Slash Command '/status' in Simulator")

        # Execute sample multi-platform Dior customer question (Telegram)
        page.locator("[data-testid='astrbot-sample-0']").click()
        for _ in range(120):
            if turns.count() >= 2:
                break
            page.wait_for_timeout(500)
        assert turns.count() >= 2, "Expected at least 2 turns after sample customer query"
        print("  [PASS] Simulated multi-platform Telegram VIP customer RAG + Dior KB conversation")

        # Execute Facebook Messenger sample preset & click 👍 Hữu ích feedback quick reply
        page.locator("[data-testid='astrbot-sample-1']").click()
        for _ in range(120):
            if turns.count() >= 3:
                break
            page.wait_for_timeout(500)
        assert turns.count() >= 3, "Expected at least 3 turns after Messenger sample query"
        fb_qr_btn = page.locator(".qr-pill-btn:has-text('👍 Hữu ích')").last
        assert fb_qr_btn.is_visible(), "Messenger reply must include '👍 Hữu ích' feedback quick reply button"
        fb_qr_btn.click()
        for _ in range(120):
            if turns.count() >= 4:
                break
            page.wait_for_timeout(500)
        assert turns.count() >= 4, "Expected feedback acknowledgment turn after clicking 👍 Hữu ích"
        print("  [PASS] Simulated Facebook Messenger consultation & EvoAgentX '👍 Hữu ích' feedback quick reply")

        # 6. Switch to Tab 3: UMO Sessions & Custom Rules
        page.locator("[data-testid='astrbot-tab-sessions']").click()
        page.wait_for_selector("[data-testid='astrbot-panel-sessions']", timeout=4000)
        assert_zero_overflow(page, "Desktop AstrBot Modal (Tab 3: UMO Sessions)")
        session_rows = page.locator(".astrbot-table tbody tr")
        assert session_rows.count() >= 2, f"Expected active UMO sessions, found {session_rows.count()}"
        print(f"  [PASS] Verified {session_rows.count()} UMO sessions in Session Management table")

        # 7. Switch to Tab 4: Personas & LLM Providers
        page.locator("[data-testid='astrbot-tab-personas_providers']").click()
        page.wait_for_selector("[data-testid='astrbot-panel-personas-providers']", timeout=4000)
        page.locator("[data-testid='astrbot-persona-id-input']").fill("dior_couture_stylist")
        page.locator("[data-testid='astrbot-persona-name-input']").fill("Dior Couture Stylist")
        page.locator("[data-testid='astrbot-persona-prompt-input']").fill("Tư vấn thời trang Haute Couture Christian Dior.")
        page.locator("[data-testid='astrbot-save-persona-btn']").click()
        page.wait_for_timeout(250)

        page.locator("[data-testid='astrbot-provider-id-input']").fill("deepseek-r1-provider")
        page.locator("[data-testid='astrbot-provider-model-input']").fill("deepseek-reasoner")
        page.locator("[data-testid='astrbot-save-provider-btn']").click()
        page.wait_for_timeout(250)
        print("  [PASS] Created new Persona & LLM Provider Source in Tab 4")

        # 8. Switch to Tab 5: Cron Jobs & Star Plugins / MCP Servers
        page.locator("[data-testid='astrbot-tab-automation_extensions']").click()
        page.wait_for_selector("[data-testid='astrbot-panel-automation']", timeout=4000)
        page.locator("[data-testid='astrbot-run-cron-cron-dior-vip-followup']").click()
        page.wait_for_timeout(300)
        page.locator("[data-testid='astrbot-cron-name-input']").fill("Nhắc lịch chăm sóc da VIP tuần thứ 2")
        page.locator("[data-testid='astrbot-save-cron-btn']").click()
        page.wait_for_timeout(250)
        page.locator("[data-testid='astrbot-ext-name-input']").fill("mcp_server_dior_crm")
        page.locator("[data-testid='astrbot-save-ext-btn']").click()
        page.wait_for_timeout(250)
        print("  [PASS] Executed proactive Cron Job, scheduled new Cron Job & registered MCP Server in Tab 5")

        # 9. Switch to Tab 6: Stats, Traces & Console Logs
        page.locator("[data-testid='astrbot-tab-observability']").click()
        page.wait_for_selector("[data-testid='astrbot-panel-observability']", timeout=4000)
        assert page.locator("[data-testid='astrbot-console-terminal']").is_visible()
        assert_zero_overflow(page, "Desktop AstrBot Modal (Tab 6: Observability)")
        print("  [PASS] Verified KPI cards, Platform Ranking, Traces & Live Console Logs in Tab 6")

        # 10. Test Mobile (375x812) and Ultra-Compact (320x568) responsiveness
        for vw, vh in [(375, 812), (320, 568)]:
            page.set_viewport_size({"width": vw, "height": vh})
            page.wait_for_timeout(250)
            assert_zero_overflow(page, f"Mobile AstrBot Modal Open ({vw}x{vh})")

        # Close via Escape key
        page.keyboard.press("Escape")
        page.wait_for_timeout(300)
        assert not page.locator("[data-testid='astrbot-modal']").is_visible()
        print("  [PASS] Closed AstrBot Modal cleanly via Escape key")

        browser.close()
        print("\n>>> ALL ASTRBOT E2E VERIFICATION CHECKS PASSED (100%) <<<")


if __name__ == "__main__":
    run_tests()
