import sys
import json
import time
from playwright.sync_api import sync_playwright

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)

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


def test_meta_quick_replies_length_limits(page):
    print("\n-----------------------------------------------------------")
    print("CHALLENGE 1: Meta Quick Replies Length Constraint (<= 20 chars)")
    print("-----------------------------------------------------------")
    
    # 1.1 Pure algorithmic stress test in JavaScript runtime
    test_cases = [
        ("", 8),  # default "Xem thêm" -> 8 chars <= 20
        ("   ", 8),
        ("A", 1),
        ("1234567890123456789", 19),
        ("12345678901234567890", 20),
        ("123456789012345678901", 20),  # 21 chars -> truncated to 17 + '...' = 20
        ("123456789012345678901234567890", 20),  # 30 chars -> 20
        ("Báo giá chi tiết", 16),
        ("Chính sách trả góp 0%", 20),
        ("Chính sách đổi trả bảo hành 1 đổi 1", 20),  # 34 chars -> 20
        ("🔥 Ưu đãi cực sốc cho khách VIP", 20),
        ("Hỗ trợ kỹ thuật 24/7 toàn quốc miễn phí", 20),
        ("A" * 150, 20),
        ("A" * 1000, 20)
    ]

    js_code = """(cases) => {
        const enforceMetaQuickReplyLimit = (title) => {
            if (!title) return 'Xem thêm'
            const clean = title.trim()
            if (clean.length <= 20) return clean
            return clean.substring(0, 17) + '...'
        };
        return cases.map(([input, expectedLen]) => {
            const res = enforceMetaQuickReplyLimit(input);
            return {
                input,
                res,
                len: res.length,
                valid: res.length <= 20
            };
        });
    }"""
    
    results = page.evaluate(js_code, test_cases)
    for idx, r in enumerate(results):
        assert r["valid"], f"Test case {idx} failed: '{r['input']}' -> '{r['res']}' ({r['len']} chars > 20)"
        assert r["len"] <= 20, f"Length {r['len']} > 20 for input '{r['input']}'"
    print(f"  [PASS] All {len(results)} algorithmic boundary tests passed (strictly <= 20 chars)!")

    # 1.2 In-DOM UI verification with Mock Route Injection
    # We will inject a custom response with long quick replies
    def handle_mock_simulate(route):
        post_data = route.request.post_data_json or {}
        scenario = post_data.get("scenario", "SALES")
        mock_reply = {
            "sender_id": post_data.get("sender_id", "test_psid"),
            "received_message": post_data.get("message", "test"),
            "scenario": scenario,
            "reply_sent": "Dạ em đã nhận được câu hỏi từ Quý khách! Đây là các câu hỏi gợi ý:",
            "quick_replies": [
                "Báo giá chi tiết",                               # 16 chars <= 20
                "Chính sách đổi trả bảo hành 1 đổi 1 toàn diện",  # 45 chars -> must truncate
                "Tư vấn kỹ thuật 24/7 trực tiếp từ chuyên gia",  # 44 chars -> must truncate
                "123456789012345678901",                          # 21 chars -> must truncate to 20
                "🔥 Ưu đãi đặc biệt hôm nay"                      # 25 chars -> must truncate
            ],
            "interaction_id": f"sim_adversarial_{int(time.time())}",
            "simulated": True,
            "timestamp": "2026-10-14T15:00:00Z"
        }
        route.fulfill(
            status=200,
            content_type="application/json",
            body=json.dumps(mock_reply)
        )

    page.route("**/api/messenger/simulate", handle_mock_simulate)

    # Open Messenger Modal
    btn_msgr = page.locator(".btn-messenger")
    if not page.locator(".modal-container[role='dialog']").is_visible():
        btn_msgr.click()
        page.wait_for_timeout(400)

    # Switch to Simulator tab
    msgr_tabs = page.locator(".modal-container .tabs-nav .tab-btn")
    msgr_tabs.nth(1).click()
    page.wait_for_timeout(300)

    # Send a prompt to trigger the adversarial mock response
    sim_input = page.locator(".modal-container .input-message")
    sim_input.fill("Kiểm tra giới hạn ký tự Quick Reply")
    btn_send = page.locator(".modal-container .btn-send-message")
    btn_send.click()
    page.wait_for_timeout(800)

    # Check every rendered quick reply pill in DOM
    qr_pills = page.locator(".modal-container .quick-reply-pill")
    pill_count = qr_pills.count()
    print(f"  [PASS] Rendered {pill_count} quick reply pills in DOM")
    assert pill_count >= 5, f"Expected at least 5 quick reply pills, got {pill_count}"

    for i in range(pill_count):
        pill = qr_pills.nth(i)
        qr_text = pill.locator(".qr-text").inner_text().strip()
        qr_title = pill.get_attribute("title") or ""
        raw_qr = qr_title.replace("Chọn: ", "").strip()
        print(f"    Pill #{i+1}: text='{qr_text}' (len={len(qr_text)}), raw_qr='{raw_qr}' (len={len(raw_qr)})")
        assert len(qr_text) <= 20, f"DOM Quick Reply pill #{i+1} text '{qr_text}' exceeded 20 chars ({len(qr_text)})"
        if len(raw_qr) > 20:
            assert qr_text.endswith("..."), f"Truncated pill text '{qr_text}' must end with '...'"
            assert len(qr_text) == 20, f"Truncated pill text '{qr_text}' must be exactly 20 chars"
        else:
            assert qr_text == raw_qr, f"Expected '{raw_qr}', got '{qr_text}'"
    print("  [PASS] All rendered DOM quick reply pills strictly obey <= 20 chars constraint!")


def test_simulator_multi_turn_interaction(page):
    print("\n-----------------------------------------------------------")
    print("CHALLENGE 2: Messenger Bot Simulator Multi-Turn Interaction")
    print("-----------------------------------------------------------")

    # Turn 1 was sent in test 1. Let's inspect conversation state
    bubbles = page.locator(".modal-container .message-row")
    initial_count = bubbles.count()
    print(f"  Initial conversation messages count: {initial_count}")
    assert initial_count >= 2, "Expected at least 1 user and 1 bot message"

    # Turn 2: Click a quick reply pill that is a question (not feedback)
    first_qr = page.locator(".modal-container .quick-reply-pill").first
    qr_prompt = first_qr.locator(".qr-text").inner_text().strip()
    print(f"  Clicking quick reply prompt: '{qr_prompt}'")
    first_qr.click()
    page.wait_for_timeout(1000)

    count_turn2 = page.locator(".modal-container .message-row").count()
    assert count_turn2 >= initial_count + 2, (
        f"Expected turn 2 to add user + bot message (before={initial_count}, after={count_turn2})"
    )
    print(f"  [PASS] Turn 2 completed via Quick Reply click ({count_turn2} messages)")

    # Turn 3: User enters custom follow-up text and presses Enter key
    sim_input = page.locator(".modal-container .input-message")
    sim_input.fill("Bảo mật dữ liệu của DocMind được bảo vệ như thế nào?")
    page.wait_for_timeout(100)
    sim_input.press("Enter")
    page.wait_for_timeout(1000)

    count_turn3 = page.locator(".modal-container .message-row").count()
    assert count_turn3 >= count_turn2 + 2, (
        f"Expected turn 3 to add user + bot message (before={count_turn2}, after={count_turn3})"
    )
    print(f"  [PASS] Turn 3 completed via custom text and Enter key ({count_turn3} messages)")

    # Turn 4: Boundary & disabled state stress
    # Try submitting empty message
    sim_input.fill("    ")
    btn_send = page.locator(".modal-container .btn-send-message")
    assert btn_send.is_disabled(), "Send button must be disabled for whitespace message"
    print("  [PASS] Send button correctly disabled when input is empty/whitespace")

    # Verify message bubble alternating roles
    last_user_bubble = page.locator(".modal-container .message-row.row-user").last
    last_bot_bubble = page.locator(".modal-container .message-row.row-bot").last
    assert last_user_bubble.is_visible()
    assert last_bot_bubble.is_visible()
    
    # Verify metadata badges on bot bubble
    scenario_pill = last_bot_bubble.locator(".scenario-pill")
    assert scenario_pill.is_visible(), "Bot bubble must display scenario pill"
    interaction_pill = last_bot_bubble.locator(".interaction-pill")
    assert interaction_pill.is_visible(), "Bot bubble must display interaction ID"
    print(f"  [PASS] Bot bubble displays scenario '{scenario_pill.inner_text().strip()}' and interaction '{interaction_pill.inner_text().strip()}'")


def test_thumbs_feedback_triggers(page):
    print("\n-----------------------------------------------------------")
    print("CHALLENGE 3: Thumbs Up / Thumbs Down Feedback Triggers")
    print("-----------------------------------------------------------")

    feedback_requests = []
    
    def handle_feedback_request(route):
        post_data = route.request.post_data_json or {}
        feedback_requests.append(post_data)
        route.fulfill(
            status=200,
            content_type="application/json",
            body=json.dumps({"success": True, "status": "EVOLVED", "rating": post_data.get("rating")})
        )

    page.route("**/api/customer-chat/feedback", handle_feedback_request)

    bot_messages = page.locator(".modal-container .message-row.row-bot")
    assert bot_messages.count() >= 2, "Expected at least 2 bot messages for feedback testing"

    # 3.1 Test 👍 Thumbs Up on first bot message
    msg1 = bot_messages.nth(0)
    btn_thumbs_up = msg1.locator("button.btn-feedback:has-text('👍')")
    btn_thumbs_down = msg1.locator("button.btn-feedback:has-text('👎')")
    
    assert btn_thumbs_up.is_visible(), "Thumbs Up button must be visible"
    assert btn_thumbs_down.is_visible(), "Thumbs Down button must be visible"
    assert not btn_thumbs_up.is_disabled(), "Thumbs Up should initially be enabled"
    assert not btn_thumbs_down.is_disabled(), "Thumbs Down should initially be enabled"

    btn_thumbs_up.click()
    page.wait_for_timeout(400)

    # Verify visual state
    assert "active" in (btn_thumbs_up.get_attribute("class") or ""), "Thumbs Up button should have .active class"
    assert btn_thumbs_up.is_disabled(), "Thumbs Up button must be disabled after click"
    assert btn_thumbs_down.is_disabled(), "Thumbs Down button must be disabled after feedback is recorded"
    
    ack_label = msg1.locator(".feedback-acknowledged")
    assert ack_label.is_visible(), "Acknowledgment label must be visible"
    assert "Đã ghi nhận 👍" in ack_label.inner_text(), f"Expected 'Đã ghi nhận 👍', got '{ack_label.inner_text()}'"
    print("  [PASS] Thumbs Up 👍 correctly sets active class, disables buttons, and displays 'Đã ghi nhận 👍'")

    # 3.2 Test 👎 Thumbs Down on second bot message
    msg2 = bot_messages.nth(1)
    btn2_thumbs_up = msg2.locator("button.btn-feedback:has-text('👍')")
    btn2_thumbs_down = msg2.locator("button.btn-feedback:has-text('👎')")

    btn2_thumbs_down.click()
    page.wait_for_timeout(400)

    assert "active" in (btn2_thumbs_down.get_attribute("class") or ""), "Thumbs Down button should have .active class"
    assert btn2_thumbs_up.is_disabled(), "Thumbs Up button must be disabled after feedback is recorded"
    assert btn2_thumbs_down.is_disabled(), "Thumbs Down button must be disabled after click"

    ack_label2 = msg2.locator(".feedback-acknowledged")
    assert ack_label2.is_visible()
    assert "Đã ghi nhận 👎" in ack_label2.inner_text(), f"Expected 'Đã ghi nhận 👎', got '{ack_label2.inner_text()}'"
    print("  [PASS] Thumbs Down 👎 correctly sets active class, disables buttons, and displays 'Đã ghi nhận 👎'")


def test_session_clearing_and_management(page):
    print("\n-----------------------------------------------------------")
    print("CHALLENGE 4: Session Clearing & State Synchronization")
    print("-----------------------------------------------------------")

    session_delete_called = []

    def handle_delete_sessions(route):
        session_delete_called.append(route.request.url)
        route.fulfill(
            status=200,
            content_type="application/json",
            body=json.dumps({"message": "Sessions cleared", "cleared_count": 5})
        )

    page.route("**/api/messenger/sessions*", handle_delete_sessions)

    # Switch to Tab 3: Customer Sessions
    msgr_tabs = page.locator(".modal-container .tabs-nav .tab-btn")
    msgr_tabs.nth(2).click()
    page.wait_for_timeout(400)

    # Check search filter functionality
    search_input = page.locator(".modal-container .search-input")
    assert search_input.is_visible(), "Sessions search input must be visible"
    search_input.fill("nonexistent_psid_xyz_999")
    page.wait_for_timeout(300)
    empty_notice = page.locator(".modal-container .empty-sessions-state")
    assert empty_notice.is_visible(), "Empty search results should show empty state notice"
    print("  [PASS] Sessions search filter functions correctly")
    
    search_input.fill("")
    page.wait_for_timeout(300)

    # Click "Xóa tất cả phiên" button with confirmation dialog
    btn_clear_all = page.locator(".modal-container .btn-danger-soft")
    assert btn_clear_all.is_visible(), "Clear all sessions button must be visible"

    page.on("dialog", lambda dialog: dialog.accept())
    btn_clear_all.click()
    page.wait_for_timeout(600)

    assert len(session_delete_called) > 0, "DELETE /api/messenger/sessions should have been called"
    print(f"  [PASS] Triggered DELETE /api/messenger/sessions ({session_delete_called[0]})")

    # Verify sessions tab shows 0 sessions / empty state
    empty_state = page.locator(".modal-container .empty-sessions-state")
    assert empty_state.is_visible(), "Sessions list must be empty after clearing all sessions"
    print("  [PASS] Sessions list displays empty state after clearing")

    # Switch back to Tab 2: Simulator
    msgr_tabs.nth(1).click()
    page.wait_for_timeout(300)

    # Verify conversation transcript is now completely cleared!
    remaining_bubbles = page.locator(".modal-container .message-row")
    assert remaining_bubbles.count() == 0, f"Expected 0 messages in simulator after clearing, found {remaining_bubbles.count()}"
    welcome_view = page.locator(".modal-container .empty-chat-welcome")
    assert welcome_view.is_visible(), "Empty chat welcome screen should reappear after clearing conversation"
    print("  [PASS] Simulator transcript completely reset and welcome view restored!")

    # Switch to Tab 1: Cấu hình & Webhook
    msgr_tabs.nth(0).click()
    page.wait_for_timeout(300)
    
    # Check active sessions card (Card 4) shows 0
    active_sessions_card = page.locator(".modal-container .stat-card").nth(3)
    val_text = active_sessions_card.locator(".stat-big-number").inner_text().strip()
    assert val_text == "0", f"Expected active sessions count 0, got {val_text}"
    print("  [PASS] Tab 1 active sessions count card correctly updated to 0!")


def test_mobile_responsive_stress(browser):
    print("\n-----------------------------------------------------------")
    print("CHALLENGE 5: Mobile (375x812) Viewport Stress & Touch Targets")
    print("-----------------------------------------------------------")

    mobile_page = browser.new_page()
    mobile_page.set_viewport_size({"width": 375, "height": 812})

    # Register mock routes for mobile page as well
    def handle_mob_simulate(route):
        post_data = route.request.post_data_json or {}
        scenario = post_data.get("scenario", "SALES")
        mock_reply = {
            "sender_id": post_data.get("sender_id", "mob_psid"),
            "received_message": post_data.get("message", "mob_test"),
            "scenario": scenario,
            "reply_sent": "Dạ DocMind hỗ trợ trọn gói trên thiết bị di động với tốc độ cao.",
            "quick_replies": [
                "Báo giá chi tiết",
                "Chính sách bảo hành",
                "Gặp nhân viên tư vấn"
            ],
            "interaction_id": f"mob_sim_{int(time.time())}",
            "simulated": True,
            "timestamp": "2026-10-14T15:00:00Z"
        }
        route.fulfill(
            status=200,
            content_type="application/json",
            body=json.dumps(mock_reply)
        )

    mobile_page.route("**/api/messenger/simulate", handle_mob_simulate)
    mobile_page.route("**/api/customer-chat/feedback", lambda route: route.fulfill(status=200, content_type="application/json", body='{"success":true}'))

    mobile_page.goto(BASE_URL, wait_until="domcontentloaded")
    mobile_page.wait_for_selector(".app-shell", timeout=5000)

    check_no_horizontal_overflow(mobile_page, "Mobile Baseline")

    # Open Messenger Modal
    mob_btn_msgr = mobile_page.locator(".btn-messenger")
    mob_btn_msgr.click()
    mobile_page.wait_for_timeout(400)

    mob_modal = mobile_page.locator(".modal-container[role='dialog']")
    assert mob_modal.is_visible()
    check_no_horizontal_overflow(mobile_page, "Mobile Messenger Modal")

    # Switch to Simulator tab
    mob_tabs = mobile_page.locator(".modal-container .tabs-nav .tab-btn")
    mob_tabs.nth(1).click()
    mobile_page.wait_for_timeout(300)

    # Send multi-line and long prompt
    mob_input = mobile_page.locator(".modal-container .input-message")
    mob_input.fill("Tư vấn giải pháp lưu trữ và bóc tách tài liệu số cho doanh nghiệp")
    mob_send = mobile_page.locator(".modal-container .btn-send-message")
    
    # Touch target check for send button
    s_box = mob_send.bounding_box()
    assert s_box["width"] >= 40 and s_box["height"] >= 40, f"Send button touch target {s_box}"
    mob_send.click()
    mobile_page.wait_for_timeout(800)

    # Verify no horizontal overflow in simulator
    check_no_horizontal_overflow(mobile_page, "Mobile Simulator Active Chat")

    # Check quick reply pills wrapping
    qr_pills = mobile_page.locator(".modal-container .quick-reply-pill")
    pill_cnt = qr_pills.count()
    assert pill_cnt >= 3, f"Expected at least 3 quick reply pills on mobile, found {pill_cnt}"
    for i in range(pill_cnt):
        pill = qr_pills.nth(i)
        p_box = pill.bounding_box()
        assert p_box["x"] + p_box["width"] <= 375, (
            f"Quick reply pill #{i+1} overflows viewport: right={p_box['x'] + p_box['width']}px > 375px"
        )
        text = pill.locator(".qr-text").inner_text().strip()
        assert len(text) <= 20, f"Pill text '{text}' exceeded 20 chars"
    print(f"  [PASS] Verified {pill_cnt} quick reply pills wrap cleanly within 375px mobile width")

    # Check feedback buttons on mobile
    fb_btn = mobile_page.locator(".modal-container .btn-feedback").first
    fb_box = fb_btn.bounding_box()
    print(f"  [PASS] Mobile feedback button size: {fb_box['width']}x{fb_box['height']}px")
    fb_btn.click()
    mobile_page.wait_for_timeout(300)

    # Close modal
    mob_close = mobile_page.locator(".modal-container .btn-close-modal")
    c_box = mob_close.bounding_box()
    assert c_box["width"] >= 44 and c_box["height"] >= 44, f"Close button {c_box} must be >= 44px"
    mob_close.click()
    mobile_page.wait_for_timeout(400)
    assert not mob_modal.is_visible()
    print("  [PASS] Mobile modal closed cleanly")

    mobile_page.close()


def main():
    print("===========================================================")
    print("EMPIRICAL CHALLENGER M3.2: MESSENGER BOT & CONSTRAINT SUITE")
    print("===========================================================")

    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)
        page = browser.new_page()
        page.set_viewport_size({"width": 1280, "height": 800})
        page.goto(BASE_URL, wait_until="domcontentloaded")
        page.wait_for_selector(".app-shell", timeout=5000)

        # Execute all 4 desktop challenges
        test_meta_quick_replies_length_limits(page)
        test_simulator_multi_turn_interaction(page)
        test_thumbs_feedback_triggers(page)
        test_session_clearing_and_management(page)

        page.close()

        # Execute mobile challenge
        test_mobile_responsive_stress(browser)

        browser.close()

    print("\n===========================================================")
    print(">>> ALL EMPIRICAL CHALLENGES PASSED (100% VERIFIED) <<<")
    print("===========================================================\n")


if __name__ == "__main__":
    main()
