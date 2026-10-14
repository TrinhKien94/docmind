import sys
import json
import time
from playwright.sync_api import sync_playwright

CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
BASE_URL = "http://localhost:3000"

results = {
    "scenario_1_rapid_toggle": {},
    "scenario_2_close_triggers": {},
    "scenario_3_viewport_resize": {},
    "scenario_4_modal_layering": {},
    "console_errors": []
}

def run_empirical_challenges():
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)
        page = browser.new_page()

        # Capture console errors and page errors
        page.on("pageerror", lambda err: results["console_errors"].append(f"PAGE_ERROR: {err}"))
        page.on("console", lambda msg: results["console_errors"].append(f"CONSOLE_{msg.type.upper()}: {msg.text}") if msg.type in ["error", "warning"] else None)

        # Mock API to return 500 immediately so demo data loads instantly without hanging on offline backend
        page.route("**/api/**", lambda route: route.fulfill(status=500, body='{"error":"offline_mock"}'))

        print("=" * 70)
        print("EMPIRICAL CHALLENGE M2.2 - MOBILE DRAWER & STATE MACHINE SUITE")
        print("=" * 70)

        # -------------------------------------------------------------
        # SCENARIO 1: RAPID TOGGLE STRESS TEST
        # -------------------------------------------------------------
        print("\n--- Running Scenario 1: Rapid Toggle Stress Test ---")
        page.set_viewport_size({"width": 375, "height": 812})
        page.goto(BASE_URL, wait_until="domcontentloaded")
        page.wait_for_selector(".app-shell", timeout=5000)
        page.wait_for_selector(".doc-card", timeout=5000)

        toggle_btn = page.locator(".btn-drawer-toggle")
        sidebar = page.locator(".sidebar-container")
        backdrop = page.locator(".mobile-drawer-backdrop")

        # Initial state check
        init_is_open = page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        init_backdrop_vis = backdrop.is_visible()
        print(f"Initial state: is-mobile-open={init_is_open}, backdrop_visible={init_backdrop_vis}")

        # Rapidly click toggle button 10 times with minimal delay (50ms)
        click_history = []
        for i in range(1, 11):
            page.evaluate("() => document.querySelector('.btn-drawer-toggle').click()")
            # Sample state immediately
            state_now = page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
            click_history.append(state_now)
            page.wait_for_timeout(50)

        print(f"Intermediate states during 10 rapid clicks: {click_history}")

        # Wait for all CSS transitions to settle (400ms)
        page.wait_for_timeout(450)

        settled_is_open = page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        settled_backdrop_vis = backdrop.is_visible()
        sidebar_rect = page.evaluate("""() => {
            const rect = document.querySelector('.sidebar-container').getBoundingClientRect();
            return { left: rect.left, right: rect.right, width: rect.width };
        }""")
        print(f"Settled state after 10 clicks: is-mobile-open={settled_is_open}, backdrop_visible={settled_backdrop_vis}, sidebar_rect={sidebar_rect}")

        # Verification: since 10 is even, drawer should be fully closed
        s1_closed_clean = (not settled_is_open) and (not settled_backdrop_vis) and (sidebar_rect["right"] <= 0)

        # Single click 11 (should open cleanly)
        page.evaluate("() => document.querySelector('.btn-drawer-toggle').click()")
        page.wait_for_timeout(400)
        click11_is_open = page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        click11_backdrop_vis = backdrop.is_visible()
        click11_rect = page.evaluate("""() => {
            const rect = document.querySelector('.sidebar-container').getBoundingClientRect();
            return { left: rect.left, right: rect.right, width: rect.width };
        }""")
        print(f"State after click #11: is-mobile-open={click11_is_open}, backdrop_visible={click11_backdrop_vis}, left={click11_rect['left']}")

        # Single click 12 (should close cleanly)
        page.evaluate("() => document.querySelector('.btn-drawer-toggle').click()")
        page.wait_for_timeout(400)
        click12_is_open = page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        click12_backdrop_vis = backdrop.is_visible()
        print(f"State after click #12: is-mobile-open={click12_is_open}, backdrop_visible={click12_backdrop_vis}")

        s1_pass = s1_closed_clean and click11_is_open and click11_backdrop_vis and (not click12_is_open) and (not click12_backdrop_vis)
        results["scenario_1_rapid_toggle"] = {
            "passed": s1_pass,
            "even_clicks_closed": s1_closed_clean,
            "click_history": click_history,
            "settled_after_10": {
                "is_mobile_open": settled_is_open,
                "backdrop_visible": settled_backdrop_vis,
                "sidebar_rect": sidebar_rect
            },
            "click_11_open": click11_is_open and click11_backdrop_vis,
            "click_12_close": (not click12_is_open) and (not click12_backdrop_vis)
        }
        print(f"Scenario 1 Verdict: {'PASS' if s1_pass else 'FAIL'}")

        # -------------------------------------------------------------
        # SCENARIO 2: CLOSE TRIGGER STRESS TEST
        # -------------------------------------------------------------
        print("\n--- Running Scenario 2: Close Trigger Stress Test ---")
        close_btn = page.locator(".btn-drawer-close")

        # 2A: Close Button
        toggle_btn.click()
        page.wait_for_timeout(400)
        assert page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        close_btn.click()
        page.wait_for_timeout(400)
        c2a_closed = not page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        c2a_backdrop = not backdrop.is_visible()
        print(f"2A (Close Button): closed={c2a_closed}, backdrop_hidden={c2a_backdrop}")

        # 2B: Backdrop Click
        toggle_btn.click()
        page.wait_for_timeout(400)
        assert page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        # Click outside drawer boundary (drawer max width is 85vw = 318.75px; click at x=360, y=200)
        page.click(".mobile-drawer-backdrop", position={"x": 360, "y": 200})
        page.wait_for_timeout(400)
        c2b_closed = not page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        c2b_backdrop = not backdrop.is_visible()
        print(f"2B (Backdrop Click at 360,200): closed={c2b_closed}, backdrop_hidden={c2b_backdrop}")

        # 2C: Escape Key
        toggle_btn.click()
        page.wait_for_timeout(400)
        assert page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        page.keyboard.press("Escape")
        page.wait_for_timeout(400)
        c2c_closed = not page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        c2c_backdrop = not backdrop.is_visible()
        print(f"2C (Escape Key): closed={c2c_closed}, backdrop_hidden={c2c_backdrop}")

        # 2D: Edge Case: Escape key pressed repeatedly when drawer is already closed
        page.keyboard.press("Escape")
        page.keyboard.press("Escape")
        page.wait_for_timeout(200)
        c2d_stable = not page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        print(f"2D (Spurious Escape when closed): remains_closed={c2d_stable}")

        s2_pass = c2a_closed and c2a_backdrop and c2b_closed and c2b_backdrop and c2c_closed and c2c_backdrop and c2d_stable
        results["scenario_2_close_triggers"] = {
            "passed": s2_pass,
            "trigger_close_btn": c2a_closed and c2a_backdrop,
            "trigger_backdrop_click": c2b_closed and c2b_backdrop,
            "trigger_escape_key": c2c_closed and c2c_backdrop,
            "spurious_escape_safe": c2d_stable
        }
        print(f"Scenario 2 Verdict: {'PASS' if s2_pass else 'FAIL'}")

        # -------------------------------------------------------------
        # SCENARIO 3: DYNAMIC VIEWPORT RESIZING
        # -------------------------------------------------------------
        print("\n--- Running Scenario 3: Dynamic Viewport Resizing ---")
        # Step 3.1: Start at Desktop 1280x800
        page.set_viewport_size({"width": 1280, "height": 800})
        page.wait_for_timeout(400)

        desktop_metrics = page.evaluate("""() => {
            const sidebar = document.querySelector('.sidebar-container');
            const chat = document.querySelector('.chat-container');
            const sStyle = window.getComputedStyle(sidebar);
            const toggle = document.querySelector('.btn-drawer-toggle');
            const tStyle = window.getComputedStyle(toggle);
            return {
                sidebarPosition: sStyle.position,
                sidebarWidth: sidebar.getBoundingClientRect().width,
                sidebarLeft: sidebar.getBoundingClientRect().left,
                chatWidth: chat.getBoundingClientRect().width,
                chatLeft: chat.getBoundingClientRect().left,
                toggleDisplay: tStyle.display,
                scrollWidth: document.documentElement.scrollWidth,
                clientWidth: document.documentElement.clientWidth,
                hasOverflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth
            };
        }""")
        print(f"Desktop 1280x800 initial: {desktop_metrics}")

        # Step 3.2: Resize down to 375x812 (mobile)
        page.set_viewport_size({"width": 375, "height": 812})
        page.wait_for_timeout(400)

        mobile_metrics = page.evaluate("""() => {
            const sidebar = document.querySelector('.sidebar-container');
            const chat = document.querySelector('.chat-container');
            const sStyle = window.getComputedStyle(sidebar);
            const toggle = document.querySelector('.btn-drawer-toggle');
            const tStyle = window.getComputedStyle(toggle);
            const rect = sidebar.getBoundingClientRect();
            return {
                sidebarPosition: sStyle.position,
                sidebarTransform: sStyle.transform,
                sidebarRight: rect.right,
                chatWidth: chat.getBoundingClientRect().width,
                chatLeft: chat.getBoundingClientRect().left,
                toggleDisplay: tStyle.display,
                scrollWidth: document.documentElement.scrollWidth,
                clientWidth: document.documentElement.clientWidth,
                hasOverflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth
            };
        }""")
        print(f"Mobile 375x812 resized: {mobile_metrics}")

        # Step 3.3: Open drawer on mobile
        toggle_btn.click()
        page.wait_for_timeout(400)
        assert page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        mobile_open_backdrop_vis = backdrop.is_visible()
        print(f"Drawer opened on mobile, backdrop_visible: {mobile_open_backdrop_vis}")

        # Step 3.4: Resize back up to 1280x800 WHILE drawer is open
        page.set_viewport_size({"width": 1280, "height": 800})
        page.wait_for_timeout(400)

        desktop_restored = page.evaluate("""() => {
            const sidebar = document.querySelector('.sidebar-container');
            const chat = document.querySelector('.chat-container');
            const sStyle = window.getComputedStyle(sidebar);
            const backdropEl = document.querySelector('.mobile-drawer-backdrop');
            const bStyle = backdropEl ? window.getComputedStyle(backdropEl) : null;
            const toggle = document.querySelector('.btn-drawer-toggle');
            const tStyle = window.getComputedStyle(toggle);
            return {
                sidebarPosition: sStyle.position,
                sidebarWidth: sidebar.getBoundingClientRect().width,
                sidebarLeft: sidebar.getBoundingClientRect().left,
                chatWidth: chat.getBoundingClientRect().width,
                chatLeft: chat.getBoundingClientRect().left,
                toggleDisplay: tStyle.display,
                backdropDisplay: bStyle ? bStyle.display : 'none',
                backdropOpacity: bStyle ? bStyle.opacity : '0',
                scrollWidth: document.documentElement.scrollWidth,
                clientWidth: document.documentElement.clientWidth,
                hasOverflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth
            };
        }""")
        print(f"Desktop 1280x800 restored from open drawer: {desktop_restored}")

        s3_pass = (
            desktop_restored["sidebarPosition"] == "relative"
            and abs(desktop_restored["sidebarWidth"] - 380) < 2
            and desktop_restored["sidebarLeft"] == 0
            and abs(desktop_restored["chatWidth"] - 900) < 2
            and desktop_restored["chatLeft"] == 380
            and not desktop_restored["hasOverflowX"]
            and desktop_restored["backdropDisplay"] == "none"
        )
        results["scenario_3_viewport_resize"] = {
            "passed": s3_pass,
            "desktop_initial": desktop_metrics,
            "mobile_resized": mobile_metrics,
            "desktop_restored": desktop_restored
        }
        print(f"Scenario 3 Verdict: {'PASS' if s3_pass else 'FAIL'}")

        # -------------------------------------------------------------
        # SCENARIO 4: DOCUMENT DETAIL MODAL LAYERING & Z-INDEX
        # -------------------------------------------------------------
        print("\n--- Running Scenario 4: Document Detail Modal Layering & Z-Index ---")
        # Resize to mobile
        page.set_viewport_size({"width": 375, "height": 812})
        page.wait_for_timeout(400)

        # Ensure drawer is open
        if not page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')"):
            toggle_btn.click()
            page.wait_for_timeout(400)

        # Find and click detail button on first document card
        detail_btn = page.locator(".documents-list .doc-card .btn-icon").first
        detail_btn.click()
        page.wait_for_timeout(400)

        modal_layering_data = page.evaluate("""() => {
            const sidebar = document.querySelector('.sidebar-container');
            const drawerBackdrop = document.querySelector('.mobile-drawer-backdrop');
            const modalBackdrop = document.querySelector('.modal-backdrop');
            const modalCard = document.querySelector('.modal-card');

            const sZIndex = sidebar ? window.getComputedStyle(sidebar).zIndex : null;
            const dbZIndex = drawerBackdrop ? window.getComputedStyle(drawerBackdrop).zIndex : null;
            const mbZIndex = modalBackdrop ? window.getComputedStyle(modalBackdrop).zIndex : null;
            const mcZIndex = modalCard ? window.getComputedStyle(modalCard).zIndex : null;

            // Probe what element is received by a click at center of screen (187, 406)
            const elCenter = document.elementFromPoint(187, 406);
            
            // Check modal card bounding box
            const mcRect = modalCard ? modalCard.getBoundingClientRect() : null;

            return {
                sidebar_zIndex: sZIndex,
                drawer_backdrop_zIndex: dbZIndex,
                modal_backdrop_zIndex: mbZIndex,
                modal_card_zIndex: mcZIndex,
                modal_card_rect: mcRect,
                element_at_screen_center: {
                    tagName: elCenter ? elCenter.tagName : null,
                    className: elCenter ? elCenter.className : null,
                    inside_modal_card: elCenter ? !!elCenter.closest('.modal-card') : false,
                    inside_sidebar: elCenter ? !!elCenter.closest('.sidebar-container') : false,
                    inside_modal_backdrop: elCenter ? !!elCenter.closest('.modal-backdrop') : false
                }
            };
        }""")
        print(f"Modal Layering Data: {json.dumps(modal_layering_data, indent=2)}")

        # Check against requirements:
        # Requirement: "renders at z-index: 1100, cleanly on top of the mobile drawer (z-index: 1000)"
        sidebar_z = int(modal_layering_data["sidebar_zIndex"]) if modal_layering_data["sidebar_zIndex"] and modal_layering_data["sidebar_zIndex"].isdigit() else 0
        modal_z = int(modal_layering_data["modal_backdrop_zIndex"]) if modal_layering_data["modal_backdrop_zIndex"] and modal_layering_data["modal_backdrop_zIndex"].isdigit() else 0

        modal_z_is_1100 = (modal_z == 1100)
        modal_on_top_of_drawer = modal_layering_data["element_at_screen_center"]["inside_modal_card"]

        s4_pass = modal_z_is_1100 and modal_on_top_of_drawer
        results["scenario_4_modal_layering"] = {
            "passed": s4_pass,
            "required_modal_z_index": 1100,
            "actual_modal_z_index": modal_z,
            "sidebar_z_index": sidebar_z,
            "modal_z_equals_1100": modal_z_is_1100,
            "modal_on_top_of_drawer": modal_on_top_of_drawer,
            "element_at_screen_center": modal_layering_data["element_at_screen_center"],
            "raw": modal_layering_data
        }
        print(f"Scenario 4 Verdict: {'PASS' if s4_pass else 'FAIL'}")
        if not s4_pass:
            print(f"FAILURE CAUSE: Modal backdrop computed z-index is {modal_z} (expected 1100). Element at screen center is inside sidebar: {modal_layering_data['element_at_screen_center']['inside_sidebar']}, inside modal: {modal_layering_data['element_at_screen_center']['inside_modal_card']}!")

        browser.close()

        # Save results to json
        with open(r"D:\docmind\frontend\tests\empirical_results.json", "w", encoding="utf-8") as f:
            json.dump(results, f, indent=2)

        print("\n" + "=" * 70)
        print("EMPIRICAL TEST SUITE COMPLETED")
        print("Summary:")
        print(f"  Scenario 1 (Rapid Toggle): {'PASS' if s1_pass else 'FAIL'}")
        print(f"  Scenario 2 (Close Triggers): {'PASS' if s2_pass else 'FAIL'}")
        print(f"  Scenario 3 (Dynamic Resizing): {'PASS' if s3_pass else 'FAIL'}")
        print(f"  Scenario 4 (Modal Layering): {'PASS' if s4_pass else 'FAIL'}")
        overall = s1_pass and s2_pass and s3_pass and s4_pass
        print(f"OVERALL OUTCOME: {'APPROVE' if overall else 'REJECT'}")
        print("=" * 70)
        return overall

if __name__ == "__main__":
    success = run_empirical_challenges()
    sys.exit(0 if success else 1)
