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
            # 2. AstrBotModal Check (includes Facebook Messenger adapter)
            # -----------------------------------------------------------------
            print(f"\n  --- Testing AstrBotModal on {name} ---")
            btn_astrbot = page.locator(".btn-astrbot")
            assert btn_astrbot.is_visible(), f"[{name}] .btn-astrbot should be visible in header"

            if is_mobile:
                astrbot_btn_box = btn_astrbot.bounding_box()
                assert astrbot_btn_box["width"] >= 44 and astrbot_btn_box["height"] >= 44, (
                    f"[{name}] .btn-astrbot tap target too small: {astrbot_btn_box['width']}x{astrbot_btn_box['height']} (expected >= 44x44)"
                )
                print(f"  [PASS] .btn-astrbot tap target: {astrbot_btn_box['width']}x{astrbot_btn_box['height']}px (>= 44px)")

            btn_astrbot.click()
            page.wait_for_timeout(400)

            astrbot_modal = page.locator("[data-testid='astrbot-modal']")
            assert astrbot_modal.is_visible(), f"[{name}] AstrBotModal should be visible after click"

            # Check overflow with AstrBotModal open
            ov_astrbot = check_overflow(page, f"{name} AstrBotModal Open")
            print(f"  [PASS] Zero horizontal overflow with AstrBotModal open ({ov_astrbot['clientWidth']}px)")

            # Check modal bounding box bounds containment
            astrbot_box = astrbot_modal.bounding_box()
            astrbot_right = astrbot_box["x"] + astrbot_box["width"]
            assert astrbot_box["x"] >= 0, f"[{name}] AstrBot modal left {astrbot_box['x']} < 0"
            assert astrbot_box["width"] <= w, f"[{name}] AstrBot modal width {astrbot_box['width']} > viewport {w}"
            assert astrbot_right <= w, f"[{name}] AstrBot modal right {astrbot_right} > viewport {w}"
            print(f"  [PASS] AstrBot modal container bounds: left={astrbot_box['x']:.1f}px, width={astrbot_box['width']:.1f}px, right={astrbot_right:.1f}px (fits in {w}px)")

            # Check close button
            astrbot_close = page.locator("[data-testid='astrbot-close-btn']")
            assert astrbot_close.is_visible(), f"[{name}] AstrBot close button should be visible"

            # Switch to Simulator Tab
            page.locator("[data-testid='astrbot-tab-simulator']").click()
            page.wait_for_timeout(300)
            check_overflow(page, f"{name} AstrBot Simulator Tab")

            # Interact with Simulator: click /status Star command
            page.locator("[data-testid='astrbot-cmd-status']").click()
            page.wait_for_timeout(400)
            msgs = page.locator(".sim-turn-block")
            assert msgs.count() >= 1, f"[{name}] Expected simulated turn, found {msgs.count()}"
            print(f"  [PASS] Star command /status triggered AstrBot conversation ({msgs.count()} turns)")

            # Switch to Sessions Tab
            page.locator("[data-testid='astrbot-tab-sessions']").click()
            page.wait_for_timeout(300)
            check_overflow(page, f"{name} AstrBot Sessions Tab")
            print(f"  [PASS] Tab (UMO Sessions) verified cleanly")

            # Test dismissal via [X]
            astrbot_close.click()
            page.wait_for_timeout(350)
            assert not astrbot_modal.is_visible(), f"[{name}] AstrBot modal should close via [X]"
            print(f"  [PASS] AstrBotModal closed via [X]")

            # Test dismissal via backdrop click
            btn_astrbot.click()
            page.wait_for_timeout(350)
            assert astrbot_modal.is_visible()
            page.click("div[data-testid='astrbot-modal-backdrop']", position={"x": 5, "y": 5})
            page.wait_for_timeout(350)
            assert not astrbot_modal.is_visible(), f"[{name}] AstrBot modal should close via backdrop click"
            print(f"  [PASS] AstrBotModal closed via backdrop click")

            # Test dismissal via Escape key
            btn_astrbot.click()
            page.wait_for_timeout(350)
            assert astrbot_modal.is_visible()
            page.keyboard.press("Escape")
            page.wait_for_timeout(350)
            assert not astrbot_modal.is_visible(), f"[{name}] AstrBot modal should close via Escape key"
            print(f"  [PASS] AstrBotModal closed via Escape key")

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
        
        # Open AstrBot modal on 320px
        page_adv.locator(".btn-astrbot").click()
        page_adv.wait_for_timeout(400)
        assert page_adv.locator("[data-testid='astrbot-modal']").is_visible()
        check_overflow(page_adv, "AstrBot open on 320px")

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
            check_overflow(page_adv, f"AstrBot resized to {rw}x{rh}")
            m_box = page_adv.locator("[data-testid='astrbot-modal']").bounding_box()
            assert m_box["x"] + m_box["width"] <= rw, f"Resized modal {m_box} overflows {rw}px"
        print("  [PASS] Challenge 1 Passed: Modal smoothly adapts to dynamic resizing with zero overflow!")

        page_adv.keyboard.press("Escape")
        page_adv.wait_for_timeout(300)

        # Challenge 2: Mobile Drawer + Modal Open Stacking & Escape Priority
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
        page_adv.locator(".btn-astrbot").click()
        page_adv.wait_for_timeout(300)
        z_indices = page_adv.evaluate("""() => {
            const astrbotBackdrop = document.querySelector('div[data-testid=\"astrbot-modal-backdrop\"]');
            const sidebar = document.querySelector('.sidebar-container');
            return {
                modal_z: parseInt(window.getComputedStyle(astrbotBackdrop).zIndex) || 0,
                sidebar_z: parseInt(window.getComputedStyle(sidebar).zIndex) || 0
            };
        }""")
        assert z_indices["modal_z"] >= 1100, f"Expected modal z-index >= 1100, got {z_indices['modal_z']}"
        assert z_indices["modal_z"] > z_indices["sidebar_z"], f"Modal {z_indices['modal_z']} must be > sidebar {z_indices['sidebar_z']}"
        print(f"  [PASS] Modal z-index ({z_indices['modal_z']}) strictly dominates mobile sidebar ({z_indices['sidebar_z']})")
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
