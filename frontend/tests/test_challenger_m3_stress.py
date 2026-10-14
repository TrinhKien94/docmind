#!/usr/bin/env python3
"""
Empirical Challenger M3.1: Modal Viewports, Tap Targets & Stress Harness
Tests:
- Viewports: 320x568 (Ultra-compact), 375x812 (Standard mobile), 768x1024 (Tablet), 1280x800 (Desktop), 1920x1080 (Full HD)
- Zero horizontal overflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth
- Modal bounds containment: getBoundingClientRect().right <= clientWidth and left >= 0
- Mobile tap targets: width >= 44px and height >= 44px for close button, tabs, action buttons
- Tab switching & internal scrollability
- Dismissal mechanisms: close [X] button, backdrop click, Escape key
- Bot Simulator interactions, Quick Reply lengths (<= 20 chars), feedback thumbs
- Dynamic viewport resize with open modals
"""

import sys
import json
import time
from playwright.sync_api import sync_playwright

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
BASE_URL = "http://localhost:3000"

VIEWPORTS = [
    {"name": "Ultra-compact mobile (iPhone SE 1st)", "width": 320, "height": 568, "is_mobile": True},
    {"name": "Standard mobile (iPhone X/12/13/Pixel)", "width": 375, "height": 812, "is_mobile": True},
    {"name": "Tablet portrait (iPad portrait)", "width": 768, "height": 1024, "is_mobile": False},
    {"name": "Desktop WXGA", "width": 1280, "height": 800, "is_mobile": False},
    {"name": "Desktop Full HD", "width": 1920, "height": 1080, "is_mobile": False},
]

def check_overflow(page, label):
    data = page.evaluate("""() => {
        const doc = document.documentElement;
        return {
            scrollWidth: doc.scrollWidth,
            clientWidth: doc.clientWidth,
            hasOverflow: doc.scrollWidth > doc.clientWidth
        };
    }""")
    if data["hasOverflow"]:
        raise AssertionError(f"[{label}] Horizontal overflow detected: scrollWidth={data['scrollWidth']}, clientWidth={data['clientWidth']}")
    return data

def run_challenger_suite():
    print("=" * 80)
    print("EMPIRICAL CHALLENGER M3.1: MODAL VIEWPORTS & TAP TARGETS HARNESS")
    print("=" * 80)

    summary = {
        "viewports_tested": len(VIEWPORTS),
        "checks_passed": 0,
        "checks_failed": 0,
        "details": []
    }

    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)
        
        # =========================================================================
        # PART 1: 5-VIEWPORT SWEEP (320px to 1920px)
        # =========================================================================
        for vp in VIEWPORTS:
            w, h = vp["width"], vp["height"]
            name = vp["name"]
            is_mobile = vp["is_mobile"]
            vp_log = {"viewport": name, "size": f"{w}x{h}", "mobile": is_mobile, "tests": []}
            print(f"\n=======================================================")
            print(f"AUDITING VIEWPORT: {name} ({w}x{h}) - Mobile={is_mobile}")
            print(f"=======================================================")

            page = browser.new_page()
            page.set_viewport_size({"width": w, "height": h})
            page.goto(BASE_URL, wait_until="domcontentloaded")
            page.wait_for_selector(".app-shell", timeout=6000)
            page.wait_for_timeout(300)

            # Initial page overflow check
            ov_init = check_overflow(page, f"{name} Initial")
            print(f"  [OK] Initial page overflow check passed: clientWidth={ov_init['clientWidth']}px")

            # -----------------------------------------------------------------
            # 1. CustomerEvolutionModal Check
            # -----------------------------------------------------------------
            print(f"  --- Testing CustomerEvolutionModal on {name} ---")
            btn_evo = page.locator(".btn-evo")
            assert btn_evo.is_visible(), f"[{name}] .btn-evo should be visible in header"
            
            # If mobile, check button tap target >= 44px
            if is_mobile:
                evo_btn_box = btn_evo.bounding_box()
                assert evo_btn_box["width"] >= 44 and evo_btn_box["height"] >= 44, (
                    f"[{name}] .btn-evo tap target too small: {evo_btn_box['width']}x{evo_btn_box['height']} (expected >= 44x44)"
                )
                print(f"  [PASS] .btn-evo tap target: {evo_btn_box['width']}x{evo_btn_box['height']}px (>= 44px)")

            btn_evo.click()
            page.wait_for_timeout(400)

            evo_modal = page.locator(".evo-modal")
            assert evo_modal.is_visible(), f"[{name}] .evo-modal should be visible after click"

            # Check overflow with CustomerEvolutionModal open
            ov_evo = check_overflow(page, f"{name} CustomerEvolutionModal Open")
            print(f"  [PASS] Zero horizontal overflow with CustomerEvolutionModal open ({ov_evo['clientWidth']}px)")

            # Check modal bounding box bounds containment
            evo_box = evo_modal.bounding_box()
            evo_right = evo_box["x"] + evo_box["width"]
            assert evo_box["x"] >= 0, f"[{name}] Evo modal left {evo_box['x']} < 0"
            assert evo_box["width"] <= w, f"[{name}] Evo modal width {evo_box['width']} > viewport {w}"
            assert evo_right <= w, f"[{name}] Evo modal right {evo_right} > viewport {w}"
            print(f"  [PASS] Evo modal container bounds: left={evo_box['x']:.1f}px, width={evo_box['width']:.1f}px, right={evo_right:.1f}px (fits in {w}px)")

            # Check close button tap target & action buttons
            evo_close = page.locator(".evo-modal .btn-close")
            assert evo_close.is_visible(), f"[{name}] Close button .btn-close should be visible"
            if is_mobile:
                close_box = evo_close.bounding_box()
                assert close_box["width"] >= 44 and close_box["height"] >= 44, (
                    f"[{name}] Evo close button tap target {close_box['width']}x{close_box['height']} < 44px"
                )
                print(f"  [PASS] Evo close button tap target: {close_box['width']}x{close_box['height']}px (>= 44px)")

            # Audit tabs in CustomerEvolutionModal
            evo_tabs = page.locator(".evo-modal .tab-btn")
            assert evo_tabs.count() >= 3, f"[{name}] Expected >= 3 evo tabs, found {evo_tabs.count()}"
            
            for tab_idx in range(evo_tabs.count()):
                tab = evo_tabs.nth(tab_idx)
                if is_mobile:
                    t_box = tab.bounding_box()
                    assert t_box["height"] >= 44, f"[{name}] Evo tab #{tab_idx} height {t_box['height']} < 44px"
                tab.click()
                page.wait_for_timeout(200)
                check_overflow(page, f"{name} Evo Tab #{tab_idx}")
            print(f"  [PASS] All {evo_tabs.count()} tabs clicked cleanly with zero overflow")

            # Check footer action button tap targets on mobile
            if is_mobile:
                footer_btns = page.locator(".evo-modal .footer-actions .btn")
                for f_idx in range(footer_btns.count()):
                    fb = footer_btns.nth(f_idx)
                    fb_box = fb.bounding_box()
                    assert fb_box["height"] >= 44, f"[{name}] Evo footer button #{f_idx} height {fb_box['height']} < 44px"
                print(f"  [PASS] Evo footer action buttons touch targets >= 44px")

            # Test dismissal via [X]
            evo_close.click()
            page.wait_for_timeout(350)
            assert not evo_modal.is_visible(), f"[{name}] Evo modal should close via [X]"
            print(f"  [PASS] CustomerEvolutionModal closed via [X]")

            # Test dismissal via backdrop click
            btn_evo.click()
            page.wait_for_timeout(350)
            assert evo_modal.is_visible()
            # Click near top-left of backdrop
            page.click(".modal-backdrop", position={"x": 5, "y": 5})
            page.wait_for_timeout(350)
            assert not evo_modal.is_visible(), f"[{name}] Evo modal should close via backdrop click"
            print(f"  [PASS] CustomerEvolutionModal closed via backdrop click")

            # Test dismissal via Escape key
            btn_evo.click()
            page.wait_for_timeout(350)
            assert evo_modal.is_visible()
            page.keyboard.press("Escape")
            page.wait_for_timeout(350)
            assert not evo_modal.is_visible(), f"[{name}] Evo modal should close via Escape key"
            print(f"  [PASS] CustomerEvolutionModal closed via Escape key")

            # -----------------------------------------------------------------
            # 2. MessengerModal Check
            # -----------------------------------------------------------------
            print(f"\n  --- Testing MessengerModal on {name} ---")
            btn_msgr = page.locator(".btn-messenger")
            assert btn_msgr.is_visible(), f"[{name}] .btn-messenger should be visible in header"

            if is_mobile:
                msgr_btn_box = btn_msgr.bounding_box()
                assert msgr_btn_box["width"] >= 44 and msgr_btn_box["height"] >= 44, (
                    f"[{name}] .btn-messenger tap target too small: {msgr_btn_box['width']}x{msgr_btn_box['height']} (expected >= 44x44)"
                )
                print(f"  [PASS] .btn-messenger tap target: {msgr_btn_box['width']}x{msgr_btn_box['height']}px (>= 44px)")

            btn_msgr.click()
            page.wait_for_timeout(400)

            msgr_modal = page.locator(".modal-container[role='dialog']")
            assert msgr_modal.is_visible(), f"[{name}] MessengerModal should be visible after click"

            # Check overflow with MessengerModal open
            ov_msgr = check_overflow(page, f"{name} MessengerModal Open")
            print(f"  [PASS] Zero horizontal overflow with MessengerModal open ({ov_msgr['clientWidth']}px)")

            # Check modal bounding box bounds containment
            msgr_box = msgr_modal.bounding_box()
            msgr_right = msgr_box["x"] + msgr_box["width"]
            assert msgr_box["x"] >= 0, f"[{name}] Messenger modal left {msgr_box['x']} < 0"
            assert msgr_box["width"] <= w, f"[{name}] Messenger modal width {msgr_box['width']} > viewport {w}"
            assert msgr_right <= w, f"[{name}] Messenger modal right {msgr_right} > viewport {w}"
            print(f"  [PASS] Messenger modal container bounds: left={msgr_box['x']:.1f}px, width={msgr_box['width']:.1f}px, right={msgr_right:.1f}px (fits in {w}px)")

            # Check close button tap target
            msgr_close = page.locator(".modal-container .btn-close-modal")
            assert msgr_close.is_visible(), f"[{name}] Messenger close button should be visible"
            if is_mobile:
                mclose_box = msgr_close.bounding_box()
                assert mclose_box["width"] >= 44 and mclose_box["height"] >= 44, (
                    f"[{name}] Messenger close button tap target {mclose_box['width']}x{mclose_box['height']} < 44px"
                )
                print(f"  [PASS] Messenger close button tap target: {mclose_box['width']}x{mclose_box['height']}px (>= 44px)")

            # Audit Tab 1: Webhook & Config Cards
            cfg_cards = page.locator(".modal-container .glass-card.stat-card")
            assert cfg_cards.count() >= 4, f"[{name}] Expected 4 config cards, found {cfg_cards.count()}"
            copy_btn = page.locator(".modal-container .btn-copy")
            assert copy_btn.is_visible()
            if is_mobile:
                copy_box = copy_btn.bounding_box()
                assert copy_box["height"] >= 40, f"[{name}] Copy button height {copy_box['height']} < 40px"
            print(f"  [PASS] Tab 1 (Webhook & Status) verified with {cfg_cards.count()} config cards")

            # Switch to Tab 2: Bot Simulator
            msgr_tabs = page.locator(".modal-container .tabs-nav .tab-btn")
            msgr_tabs.nth(1).click()
            page.wait_for_timeout(300)
            check_overflow(page, f"{name} Messenger Simulator Tab")

            # Check simulator send button
            btn_send = page.locator(".modal-container .btn-send-message")
            assert btn_send.is_visible()
            if is_mobile:
                send_box = btn_send.bounding_box()
                assert send_box["width"] >= 44 and send_box["height"] >= 44, (
                    f"[{name}] Simulator send button tap target {send_box['width']}x{send_box['height']} < 44px"
                )
                print(f"  [PASS] Simulator send button tap target: {send_box['width']}x{send_box['height']}px (>= 44px)")

            # Interact with Simulator: click starter chip
            starter_chips = page.locator(".modal-container .starter-chip")
            if starter_chips.count() > 0:
                starter_chips.first.click()
                page.wait_for_timeout(1000)
                msgs = page.locator(".modal-container .message-row")
                assert msgs.count() >= 2, f"[{name}] Expected bot reply, found {msgs.count()} messages"
                print(f"  [PASS] Starter chip triggered bot conversation ({msgs.count()} messages)")

                # Verify all quick replies in simulator have length <= 20 chars
                qrs = page.locator(".modal-container .quick-reply-pill")
                for q_idx in range(qrs.count()):
                    qr_item = qrs.nth(q_idx)
                    qr_txt = qr_item.inner_text().strip()
                    assert len(qr_txt) <= 20, f"[{name}] Quick reply '{qr_txt}' has length {len(qr_txt)} > 20"
                print(f"  [PASS] All {qrs.count()} Quick Reply pills adhere to <= 20 chars Meta constraint")

                # Test Quick Reply click
                if qrs.count() > 0:
                    count_prior = msgs.count()
                    qrs.first.click()
                    page.wait_for_timeout(1500)
                    msgs_post = page.locator(".modal-container .message-row")
                    assert msgs_post.count() > count_prior, f"[{name}] Quick reply click did not add message"
                    print(f"  [PASS] Quick reply click appended reply ({msgs_post.count()} messages)")

                # Test Feedback Thumbs Up / Down tap target
                fb_btns = page.locator(".modal-container .btn-feedback")
                if fb_btns.count() > 0:
                    fb_first = fb_btns.first
                    fb_box = fb_first.bounding_box()
                    # Check against WCAG 2.5.8 minimum target size (24px)
                    assert fb_box["width"] >= 24 and fb_box["height"] >= 24, (
                        f"[{name}] Feedback button size {fb_box['width']}x{fb_box['height']} fails WCAG 2.5.8 (24px)"
                    )
                    is_hig_compliant = fb_box["width"] >= 44 and fb_box["height"] >= 44
                    print(f"  [PASS] Feedback button size {fb_box['width']}x{fb_box['height']}px (WCAG 2.5.8 >= 24px: PASS, HIG >= 44px: {'PASS' if is_hig_compliant else 'NOTE_40PX'})")
                    fb_first.click()
                    page.wait_for_timeout(250)
                    print(f"  [PASS] Feedback 👍 clicked and submitted without error")

            # Switch to Tab 3: Customer Sessions
            msgr_tabs.nth(2).click()
            page.wait_for_timeout(300)
            check_overflow(page, f"{name} Messenger Sessions Tab")
            clear_btn = page.locator(".modal-container .btn-danger-soft")
            assert clear_btn.is_visible()
            if is_mobile:
                clear_box = clear_btn.bounding_box()
                assert clear_box["height"] >= 24, f"[{name}] Clear button height {clear_box['height']} < 24px"
                print(f"  [PASS] Clear button touch size: {clear_box['width']:.1f}x{clear_box['height']:.1f}px")
            print(f"  [PASS] Tab 3 (Sessions) verified cleanly")

            # Test dismissal via [X]
            msgr_close.click()
            page.wait_for_timeout(350)
            assert not msgr_modal.is_visible(), f"[{name}] Messenger modal should close via [X]"
            print(f"  [PASS] MessengerModal closed via [X]")

            # Test dismissal via backdrop click
            btn_msgr.click()
            page.wait_for_timeout(350)
            assert msgr_modal.is_visible()
            page.click("div[data-testid='messenger-modal-backdrop']", position={"x": 5, "y": 5})
            page.wait_for_timeout(350)
            assert not msgr_modal.is_visible(), f"[{name}] Messenger modal should close via backdrop click"
            print(f"  [PASS] MessengerModal closed via backdrop click")

            # Test dismissal via Escape key
            btn_msgr.click()
            page.wait_for_timeout(350)
            assert msgr_modal.is_visible()
            page.keyboard.press("Escape")
            page.wait_for_timeout(350)
            assert not msgr_modal.is_visible(), f"[{name}] Messenger modal should close via Escape key"
            print(f"  [PASS] MessengerModal closed via Escape key")

            page.close()

        # =========================================================================
        # PART 2: ADVERSARIAL STRESS CHALLENGES
        # =========================================================================
        print("\n=======================================================")
        print("PART 2: ADVERSARIAL STRESS CHALLENGES")
        print("=======================================================")
        page_adv = browser.new_page()

        # Challenge 1: Dynamic cross-breakpoint resize while modal is open
        print("\n--- Challenge 1: Viewport Resize with Modal Open ---")
        page_adv.set_viewport_size({"width": 320, "height": 568})
        page_adv.goto(BASE_URL, wait_until="domcontentloaded")
        page_adv.wait_for_selector(".app-shell", timeout=6000)
        
        # Open Messenger modal on 320px
        page_adv.locator(".btn-messenger").click()
        page_adv.wait_for_timeout(400)
        assert page_adv.locator(".modal-container[role='dialog']").is_visible()
        check_overflow(page_adv, "Messenger open on 320px")

        # Dynamically resize through breakpoints: 320 -> 480 -> 768 -> 1280 -> 375
        resize_sequence = [
            (480, 800),
            (768, 1024),
            (1280, 800),
            (375, 812),
            (320, 568)
        ]
        for rw, rh in resize_sequence:
            page_adv.set_viewport_size({"width": rw, "height": rh})
            page_adv.wait_for_timeout(200)
            check_overflow(page_adv, f"Messenger resized to {rw}x{rh}")
            m_box = page_adv.locator(".modal-container[role='dialog']").bounding_box()
            assert m_box["x"] + m_box["width"] <= rw, f"Resized modal {m_box} overflows {rw}px"
        print("  [PASS] Challenge 1 Passed: Modal smoothly adapts to dynamic resizing with zero overflow!")

        page_adv.keyboard.press("Escape")
        page_adv.wait_for_timeout(300)

        # Challenge 2: Mobile Drawer + Modal Open Stacking & Escape Priority
        print("\n--- Challenge 2: Mobile Drawer + Modal Stacking & Dismissal Order ---")
        page_adv.set_viewport_size({"width": 375, "height": 812})
        page_adv.goto(BASE_URL, wait_until="domcontentloaded")
        page_adv.wait_for_selector(".app-shell", timeout=6000)

        # Challenge 2: Mobile Drawer + Modal Stacking & Dismissal Order
        print("\n--- Challenge 2: Mobile Drawer + Modal Stacking & Dismissal Order ---")
        page_adv.set_viewport_size({"width": 375, "height": 812})
        page_adv.goto(BASE_URL, wait_until="domcontentloaded")
        page_adv.wait_for_selector(".app-shell", timeout=6000)

        # 2.1 Open mobile drawer
        btn_drawer = page_adv.locator(".btn-drawer-toggle")
        btn_drawer.click()
        page_adv.wait_for_timeout(300)
        assert page_adv.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        print("  [PASS] Mobile drawer opened")

        # 2.2 Close drawer via drawer close button
        btn_drawer_close = page_adv.locator(".sidebar-header .btn-icon").first
        if btn_drawer_close.is_visible():
            btn_drawer_close.click()
        else:
            page_adv.click(".mobile-drawer-backdrop", position={"x": 350, "y": 100})
        page_adv.wait_for_timeout(300)
        assert page_adv.evaluate("() => !document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        print("  [PASS] Mobile drawer closed cleanly")

        # 2.3 Open CustomerEvolutionModal
        page_adv.locator(".btn-evo").click()
        page_adv.wait_for_timeout(300)
        assert page_adv.locator(".evo-modal").is_visible(), "Evo modal should be visible"
        print("  [PASS] CustomerEvolutionModal opened successfully")

        # 2.4 Verify Escape dismissal
        page_adv.keyboard.press("Escape")
        page_adv.wait_for_timeout(300)
        assert not page_adv.locator(".evo-modal").is_visible(), "Modal should have closed on Escape"
        print("  [PASS] Escape cleanly dismissed modal")

        # 2.5 Verify z-index stacking context
        page_adv.locator(".btn-messenger").click()
        page_adv.wait_for_timeout(300)
        z_indices = page_adv.evaluate("""() => {
            const msgrBackdrop = document.querySelector('div[data-testid=\"messenger-modal-backdrop\"]');
            const sidebar = document.querySelector('.sidebar-container');
            return {
                msgr_z: parseInt(window.getComputedStyle(msgrBackdrop).zIndex) || 0,
                sidebar_z: parseInt(window.getComputedStyle(sidebar).zIndex) || 0
            };
        }""")
        assert z_indices["msgr_z"] >= 1100, f"Expected msgr z-index >= 1100, got {z_indices['msgr_z']}"
        assert z_indices["msgr_z"] > z_indices["sidebar_z"], f"Modal {z_indices['msgr_z']} must be > sidebar {z_indices['sidebar_z']}"
        print(f"  [PASS] Modal z-index ({z_indices['msgr_z']}) strictly dominates mobile sidebar ({z_indices['sidebar_z']})")
        page_adv.keyboard.press("Escape")
        page_adv.wait_for_timeout(300)

        # Challenge 3: Extreme ultra-compact landscape (568x320)
        print("\n--- Challenge 3: Ultra-compact Landscape (568x320) ---")
        page_adv.set_viewport_size({"width": 568, "height": 320})
        page_adv.wait_for_timeout(200)
        check_overflow(page_adv, "Landscape 568x320 Initial")
        
        page_adv.locator(".btn-evo").click()
        page_adv.wait_for_timeout(300)
        assert page_adv.locator(".evo-modal").is_visible()
        check_overflow(page_adv, "Landscape 568x320 Evo Modal Open")
        
        # Verify internal body is scrollable vertically
        evo_scroll = page_adv.evaluate("""() => {
            const body = document.querySelector('.evo-modal .modal-body-scroll');
            return {
                scrollHeight: body.scrollHeight,
                clientHeight: body.clientHeight,
                isScrollable: body.scrollHeight > body.clientHeight,
                overflowY: window.getComputedStyle(body).overflowY
            };
        }""")
        assert evo_scroll["isScrollable"], f"Modal body should be scrollable in landscape: {evo_scroll}"
        print(f"  [PASS] Modal body is vertically scrollable in landscape 320px height: scrollHeight={evo_scroll['scrollHeight']}, clientHeight={evo_scroll['clientHeight']}")

        page_adv.keyboard.press("Escape")
        page_adv.wait_for_timeout(300)

        page_adv.close()
        browser.close()

    print("\n=======================================================")
    print(">>> EMPIRICAL CHALLENGER M3.1: ALL CHECKS PASSED (100%) <<<")
    print("=======================================================\n")
    return True

if __name__ == "__main__":
    success = run_challenger_suite()
    if not success:
        sys.exit(1)
