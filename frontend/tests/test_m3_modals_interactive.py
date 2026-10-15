import sys
import time
from playwright.sync_api import sync_playwright

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
BASE_URL = "http://localhost:3000"


def check_no_horizontal_overflow(page, context_label=""):
    overflow = page.evaluate("""() => {
        const doc = document.documentElement;
        return {
            scrollWidth: doc.scrollWidth,
            clientWidth: doc.clientWidth,
            hasOverflow: doc.scrollWidth > doc.clientWidth
        };
    }""")
    assert not overflow["hasOverflow"], (
        f"[{context_label}] Horizontal overflow detected! "
        f"scrollWidth={overflow['scrollWidth']}, clientWidth={overflow['clientWidth']}"
    )
    print(f"  [OK] No horizontal overflow in {context_label} ({overflow['clientWidth']}px)")


def run_tests():
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)

        print("\n=======================================================")
        print("STAGE 1: DESKTOP (1280x800) INTERACTIVE MODAL AUDIT")
        print("=======================================================")
        page = browser.new_page()
        page.set_viewport_size({"width": 1280, "height": 800})
        page.goto(BASE_URL, wait_until="domcontentloaded")
        page.wait_for_selector(".app-shell", timeout=5000)

        check_no_horizontal_overflow(page, "Desktop Initial")

        # --- 1.1 CustomerEvolutionModal (Desktop) ---
        print("\n--- Testing CustomerEvolutionModal on Desktop ---")
        btn_evo = page.locator(".btn-evo")
        assert btn_evo.is_visible(), "Button .btn-evo must be visible in header"
        btn_evo.click()
        page.wait_for_timeout(400)

        evo_modal = page.locator(".evo-modal")
        assert evo_modal.is_visible(), "CustomerEvolutionModal (.evo-modal) must be visible after click"
        print("  [PASS] CustomerEvolutionModal opened successfully")

        # Verify 4 Metric cards
        metric_cards = page.locator(".evo-modal .metric-card")
        assert metric_cards.count() == 4, f"Expected 4 metric cards, found {metric_cards.count()}"
        print(f"  [PASS] All 4 metric cards rendered cleanly")

        # Check tabs navigation
        tab_buttons = page.locator(".evo-modal .tab-btn")
        assert tab_buttons.count() >= 3, "Expected at least 3 tabs in evolution modal"
        
        # Click Tab 2: Exemplars MIPRO
        tab_buttons.nth(1).click()
        page.wait_for_timeout(300)
        exemplar_cards = page.locator(".evo-modal .exemplar-card")
        print(f"  [PASS] Switched to MIPRO Exemplars tab ({exemplar_cards.count()} exemplars visible)")

        # Click Tab 3: Textual Gradients
        tab_buttons.nth(2).click()
        page.wait_for_timeout(300)
        gradient_items = page.locator(".evo-modal .gradient-timeline-item")
        print(f"  [PASS] Switched to Textual Gradients tab ({gradient_items.count()} gradient entries)")

        # Click Tab 1: Guidelines
        tab_buttons.nth(0).click()
        page.wait_for_timeout(300)
        guideline_cards = page.locator(".evo-modal .guideline-card")
        print(f"  [PASS] Switched back to Guidelines tab ({guideline_cards.count()} guidelines)")

        check_no_horizontal_overflow(page, "Desktop CustomerEvolutionModal Open")

        # Test Close via [X] button
        btn_close_evo = page.locator(".evo-modal .btn-close")
        assert btn_close_evo.is_visible(), "Close button must be visible in evolution modal"
        btn_close_evo.click()
        page.wait_for_timeout(400)
        assert not evo_modal.is_visible(), "Evolution modal must be closed after clicking [X]"
        print("  [PASS] Closed CustomerEvolutionModal via [X] button")

        # Re-open and close via backdrop click
        btn_evo.click()
        page.wait_for_timeout(400)
        assert evo_modal.is_visible()
        page.click(".modal-backdrop", position={"x": 50, "y": 50})
        page.wait_for_timeout(400)
        assert not evo_modal.is_visible(), "Evolution modal must close on backdrop click"
        print("  [PASS] Closed CustomerEvolutionModal via backdrop click")

        # Re-open and close via Escape key
        btn_evo.click()
        page.wait_for_timeout(400)
        assert evo_modal.is_visible()
        page.keyboard.press("Escape")
        page.wait_for_timeout(400)
        assert not evo_modal.is_visible(), "Evolution modal must close on Escape key"
        print("  [PASS] Closed CustomerEvolutionModal via Escape key")

        # --- 1.2 AstrBotModal (Desktop - includes Facebook Messenger adapter) ---
        print("\n--- Testing AstrBotModal on Desktop ---")
        btn_astrbot = page.locator(".btn-astrbot")
        assert btn_astrbot.is_visible(), "Button .btn-astrbot must be visible in header"
        btn_astrbot.click()
        page.wait_for_timeout(400)

        astrbot_modal = page.locator("[data-testid='astrbot-modal']")
        assert astrbot_modal.is_visible(), "AstrBotModal ([data-testid='astrbot-modal']) must be visible"
        print("  [PASS] AstrBotModal opened successfully")

        # Verify Tab 1: Overview & Platform Adapters (including Facebook Messenger)
        bot_items = page.locator("[data-testid^='astrbot-bot-item-']")
        assert bot_items.count() >= 5, f"Expected >= 5 bot items, found {bot_items.count()}"
        assert page.locator("[data-testid='astrbot-bot-item-messenger_dior_page']").is_visible(), "Facebook Messenger bot item must be visible in AstrBot"
        print(f"  [PASS] Overview & Platforms verified with {bot_items.count()} adapters (including messenger_dior_page)")

        # Switch to Live Simulator tab
        page.locator("[data-testid='astrbot-tab-simulator']").click()
        page.wait_for_timeout(300)
        page.locator("[data-testid='astrbot-cmd-status']").click()
        page.wait_for_timeout(400)
        turns = page.locator(".sim-turn-block")
        assert turns.count() >= 1, "Expected simulated turn after /status command"
        print(f"  [PASS] Switched to AstrBot Simulator tab and executed /status ({turns.count()} turns)")

        # Switch to UMO Sessions tab
        page.locator("[data-testid='astrbot-tab-sessions']").click()
        page.wait_for_timeout(300)
        session_rows = page.locator(".astrbot-table tbody tr")
        assert session_rows.count() >= 1, "Expected UMO session rows in table"
        print("  [PASS] Switched to UMO Sessions tab successfully")

        check_no_horizontal_overflow(page, "Desktop AstrBotModal Open")

        # Test Close via [X] button
        btn_close_astrbot = page.locator("[data-testid='astrbot-close-btn']")
        assert btn_close_astrbot.is_visible(), "Close button must be visible in AstrBot modal"
        btn_close_astrbot.click()
        page.wait_for_timeout(400)
        assert not astrbot_modal.is_visible(), "AstrBot modal must close after clicking [X]"
        print("  [PASS] Closed AstrBotModal via [X] button")

        # Re-open and close via backdrop click
        btn_astrbot.click()
        page.wait_for_timeout(400)
        assert astrbot_modal.is_visible()
        page.click("div[data-testid='astrbot-modal-backdrop']", position={"x": 5, "y": 5})
        page.wait_for_timeout(400)
        assert not astrbot_modal.is_visible(), "AstrBot modal must close on backdrop click"
        print("  [PASS] Closed AstrBotModal via backdrop click")

        # Re-open and close via Escape key
        btn_astrbot.click()
        page.wait_for_timeout(400)
        assert astrbot_modal.is_visible()
        page.keyboard.press("Escape")
        page.wait_for_timeout(400)
        assert not astrbot_modal.is_visible(), "AstrBot modal must close on Escape key"
        print("  [PASS] Closed AstrBotModal via Escape key")

        page.close()

        print("\n=======================================================")
        print("STAGE 2: MOBILE (375x812) RESPONSIVE MODAL AUDIT")
        print("=======================================================")
        mobile_page = browser.new_page()
        mobile_page.set_viewport_size({"width": 375, "height": 812})
        mobile_page.goto(BASE_URL, wait_until="domcontentloaded")
        mobile_page.wait_for_selector(".app-shell", timeout=5000)

        check_no_horizontal_overflow(mobile_page, "Mobile Initial")

        # --- 2.1 CustomerEvolutionModal (Mobile) ---
        print("\n--- Testing CustomerEvolutionModal on Mobile (375x812) ---")
        mob_btn_evo = mobile_page.locator(".btn-evo")
        assert mob_btn_evo.is_visible(), "Button .btn-evo must be visible on mobile header"
        box = mob_btn_evo.bounding_box()
        assert box["width"] >= 44 and box["height"] >= 44, f"Evo button size {box} must be >= 44px"
        print(f"  [PASS] Mobile Evo button has touch target {box['width']}x{box['height']}px (>= 44px)")

        mob_btn_evo.click()
        mobile_page.wait_for_timeout(400)

        mob_evo_modal = mobile_page.locator(".evo-modal")
        assert mob_evo_modal.is_visible(), "CustomerEvolutionModal must be visible on mobile"
        evo_rect = mob_evo_modal.bounding_box()
        evo_right = evo_rect["x"] + evo_rect["width"]
        assert evo_rect["width"] <= 375, f"Evo modal width {evo_rect['width']} exceeds 375px mobile viewport"
        assert evo_right <= 375, f"Evo modal right {evo_right} clips viewport"
        print(f"  [PASS] Evo modal container fits mobile viewport: width={evo_rect['width']}px, right={evo_right}px")

        check_no_horizontal_overflow(mobile_page, "Mobile CustomerEvolutionModal Open")

        # Verify touch targets for mobile close button
        mob_evo_close = mobile_page.locator(".evo-modal .btn-close")
        c_box = mob_evo_close.bounding_box()
        assert c_box["width"] >= 44 and c_box["height"] >= 44, f"Close button size {c_box} must be >= 44px"
        print(f"  [PASS] Evolution close button touch target: {c_box['width']}x{c_box['height']}px (>= 44px)")

        # Verify mobile tabs scrollable and switch
        mob_evo_tabs = mobile_page.locator(".evo-modal .tab-btn")
        mob_evo_tabs.nth(1).click()
        mobile_page.wait_for_timeout(300)
        print("  [PASS] Switched to Exemplars tab on mobile")

        mob_evo_close.click()
        mobile_page.wait_for_timeout(400)
        assert not mob_evo_modal.is_visible()
        print("  [PASS] Closed CustomerEvolutionModal on mobile")

        # --- 2.2 AstrBotModal (Mobile) ---
        print("\n--- Testing AstrBotModal on Mobile (375x812) ---")
        mob_btn_astrbot = mobile_page.locator(".btn-astrbot")
        assert mob_btn_astrbot.is_visible(), "Button .btn-astrbot must be visible on mobile header"
        m_box = mob_btn_astrbot.bounding_box()
        assert m_box["width"] >= 44 and m_box["height"] >= 44, f"AstrBot button size {m_box} must be >= 44px"
        print(f"  [PASS] Mobile AstrBot button touch target: {m_box['width']}x{m_box['height']}px (>= 44px)")

        mob_btn_astrbot.click()
        mobile_page.wait_for_timeout(400)

        mob_astrbot_modal = mobile_page.locator("[data-testid='astrbot-modal']")
        assert mob_astrbot_modal.is_visible(), "AstrBotModal must be visible on mobile"
        astrbot_rect = mob_astrbot_modal.bounding_box()
        astrbot_right = astrbot_rect["x"] + astrbot_rect["width"]
        assert astrbot_rect["width"] <= 375, f"AstrBot modal width {astrbot_rect['width']} exceeds 375px"
        assert astrbot_right <= 375, f"AstrBot modal right {astrbot_right} clips viewport"
        print(f"  [PASS] AstrBot modal container fits mobile viewport: width={astrbot_rect['width']}px, right={astrbot_right}px")

        check_no_horizontal_overflow(mobile_page, "Mobile AstrBotModal Open")

        mob_astrbot_close = mobile_page.locator("[data-testid='astrbot-close-btn']")
        mob_astrbot_close.click()
        mobile_page.wait_for_timeout(400)
        assert not mob_astrbot_modal.is_visible()
        print("  [PASS] Closed AstrBotModal on mobile")

        mobile_page.close()
        browser.close()

        print("\n=======================================================")
        print(">>> ALL MODALS INTERACTIVE TESTS PASSED (100%) <<<")
        print("=======================================================\n")


if __name__ == "__main__":
    run_tests()
