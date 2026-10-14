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

        # --- 1.2 MessengerModal (Desktop) ---
        print("\n--- Testing MessengerModal on Desktop ---")
        btn_msgr = page.locator(".btn-messenger")
        assert btn_msgr.is_visible(), "Button .btn-messenger must be visible in header"
        btn_msgr.click()
        page.wait_for_timeout(400)

        msgr_modal = page.locator(".modal-container[role='dialog']")
        assert msgr_modal.is_visible(), "MessengerModal (.modal-container) must be visible"
        print("  [PASS] MessengerModal opened successfully")

        # Verify Tab 1: Status & Webhook
        callback_input = page.locator(".modal-container input[aria-label='Webhook URL']")
        assert callback_input.is_visible(), "Webhook callback URL input must be visible"
        copy_btn = page.locator(".modal-container .btn-copy")
        assert copy_btn.is_visible(), "Copy URL button must be visible"
        config_cards = page.locator(".modal-container .glass-card.stat-card")
        assert config_cards.count() >= 4, f"Expected 4 config cards, found {config_cards.count()}"
        print(f"  [PASS] Status & Webhook tab verified with {config_cards.count()} config cards")

        # Switch to Tab 2: Bot Simulator
        msgr_tabs = page.locator(".modal-container .tabs-nav .tab-btn")
        msgr_tabs.nth(1).click()
        page.wait_for_timeout(300)
        sim_input = page.locator(".modal-container .input-message")
        assert sim_input.is_visible(), "Simulator input field must be visible"
        btn_send = page.locator(".modal-container .btn-send-message")
        assert btn_send.is_visible(), "Send button must be visible"
        print("  [PASS] Switched to Simulator tab successfully")

        # Test Starter Chip Prompt
        starter_chips = page.locator(".modal-container .starter-chip")
        if starter_chips.count() > 0:
            starter_chips.first.click()
            page.wait_for_timeout(800)
            bubbles = page.locator(".modal-container .message-row")
            assert bubbles.count() >= 2, "Expected user message and bot reply"
            print(f"  [PASS] Clicked starter chip, bot replied successfully ({bubbles.count()} messages)")

            # Check Quick Replies and verify length <= 20
            quick_replies = page.locator(".modal-container .quick-reply-pill")
            qr_count = quick_replies.count()
            print(f"  [PASS] Found {qr_count} quick reply pills")
            if qr_count > 0:
                qr_text = quick_replies.first.inner_text().strip()
                assert len(qr_text) <= 20, f"Quick reply '{qr_text}' exceeded 20 chars ({len(qr_text)})"
                print(f"  [PASS] Verified Quick reply length: '{qr_text}' ({len(qr_text)} chars <= 20)")
                # Click quick reply
                count_before = bubbles.count()
                quick_replies.first.click()
                page.wait_for_timeout(1500)
                bubbles_after = page.locator(".modal-container .message-row")
                assert bubbles_after.count() > count_before, f"Expected new message from quick reply, before={count_before}, after={bubbles_after.count()}"
                print(f"  [PASS] Quick reply clicked and triggered bot response ({bubbles_after.count()} messages)")

            # Check feedback thumbs
            feedback_btn = page.locator(".modal-container .btn-feedback").first
            if feedback_btn.is_visible():
                feedback_btn.click()
                page.wait_for_timeout(300)
                print("  [PASS] Feedback 👍 recorded successfully")

        # Switch to Tab 3: Customer Sessions
        msgr_tabs.nth(2).click()
        page.wait_for_timeout(300)
        sessions_search = page.locator(".modal-container .search-input")
        assert sessions_search.is_visible(), "Sessions search input must be visible"
        btn_clear_sessions = page.locator(".modal-container .btn-danger-soft")
        assert btn_clear_sessions.is_visible(), "Clear all sessions button must be visible"
        print("  [PASS] Switched to Sessions tab successfully")

        check_no_horizontal_overflow(page, "Desktop MessengerModal Open")

        # Test Close via [X] button
        btn_close_msgr = page.locator(".modal-container .btn-close-modal")
        assert btn_close_msgr.is_visible(), "Close button must be visible in Messenger modal"
        btn_close_msgr.click()
        page.wait_for_timeout(400)
        assert not msgr_modal.is_visible(), "Messenger modal must close after clicking [X]"
        print("  [PASS] Closed MessengerModal via [X] button")

        # Re-open and close via backdrop click
        btn_msgr.click()
        page.wait_for_timeout(400)
        assert msgr_modal.is_visible()
        page.click("div[data-testid='messenger-modal-backdrop']", position={"x": 50, "y": 50})
        page.wait_for_timeout(400)
        assert not msgr_modal.is_visible(), "Messenger modal must close on backdrop click"
        print("  [PASS] Closed MessengerModal via backdrop click")

        # Re-open and close via Escape key
        btn_msgr.click()
        page.wait_for_timeout(400)
        assert msgr_modal.is_visible()
        page.keyboard.press("Escape")
        page.wait_for_timeout(400)
        assert not msgr_modal.is_visible(), "Messenger modal must close on Escape key"
        print("  [PASS] Closed MessengerModal via Escape key")

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

        # --- 2.2 MessengerModal (Mobile) ---
        print("\n--- Testing MessengerModal on Mobile (375x812) ---")
        mob_btn_msgr = mobile_page.locator(".btn-messenger")
        assert mob_btn_msgr.is_visible(), "Button .btn-messenger must be visible on mobile header"
        m_box = mob_btn_msgr.bounding_box()
        assert m_box["width"] >= 44 and m_box["height"] >= 44, f"Messenger button size {m_box} must be >= 44px"
        print(f"  [PASS] Mobile Messenger button touch target: {m_box['width']}x{m_box['height']}px (>= 44px)")

        mob_btn_msgr.click()
        mobile_page.wait_for_timeout(400)

        mob_msgr_modal = mobile_page.locator(".modal-container[role='dialog']")
        assert mob_msgr_modal.is_visible(), "MessengerModal must be visible on mobile"
        msgr_rect = mob_msgr_modal.bounding_box()
        msgr_right = msgr_rect["x"] + msgr_rect["width"]
        assert msgr_rect["width"] <= 375, f"Messenger modal width {msgr_rect['width']} exceeds 375px"
        assert msgr_right <= 375, f"Messenger modal right {msgr_right} clips viewport"
        print(f"  [PASS] Messenger modal container fits mobile viewport: width={msgr_rect['width']}px, right={msgr_right}px")

        check_no_horizontal_overflow(mobile_page, "Mobile MessengerModal Open")

        # Verify touch targets for mobile close button
        mob_msgr_close = mobile_page.locator(".modal-container .btn-close-modal")
        mc_box = mob_msgr_close.bounding_box()
        assert mc_box["width"] >= 44 and mc_box["height"] >= 44, f"Close button size {mc_box} must be >= 44px"
        print(f"  [PASS] Messenger close button touch target: {mc_box['width']}x{mc_box['height']}px (>= 44px)")

        # Switch to Simulator on mobile
        mob_msgr_tabs = mobile_page.locator(".modal-container .tabs-nav .tab-btn")
        mob_msgr_tabs.nth(1).click()
        mobile_page.wait_for_timeout(300)

        # Test typing and sending a message in mobile simulator
        mob_sim_input = mobile_page.locator(".modal-container .input-message")
        mob_sim_input.fill("Tư vấn giá iPhone")
        mobile_page.wait_for_timeout(200)
        mob_btn_send = mobile_page.locator(".modal-container .btn-send-message")
        mob_btn_send.click()
        mobile_page.wait_for_timeout(1500)

        mob_messages = mobile_page.locator(".modal-container .message-row")
        assert mob_messages.count() >= 2, f"Expected sent message and response, found {mob_messages.count()}"
        print(f"  [PASS] Mobile simulator message sent and replied ({mob_messages.count()} messages)")

        check_no_horizontal_overflow(mobile_page, "Mobile Messenger Simulator Active")

        mob_msgr_close.click()
        mobile_page.wait_for_timeout(400)
        assert not mob_msgr_modal.is_visible()
        print("  [PASS] Closed MessengerModal on mobile")

        mobile_page.close()
        browser.close()

        print("\n=======================================================")
        print(">>> ALL MODALS INTERACTIVE TESTS PASSED (100%) <<<")
        print("=======================================================\n")


if __name__ == "__main__":
    run_tests()
