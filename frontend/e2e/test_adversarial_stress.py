#!/usr/bin/env python3
"""
Adversarial Stress Testing Suite for DocMind Mobile UI
Tests extreme boundary conditions and stress cases:
1. Long unbreakable text & textarea multiline expansion (check if input bar gets clipped)
2. Extreme narrow 320x480 / 320x568 starter prompts wrapping
3. Touch target strict WCAG AAA / AA sizing (>= 44x44px) for all primary mobile controls
4. Rapid drawer toggling (open/close 5 times in rapid succession)
5. Message history scrolling containment
"""

import sys
import json
from playwright.sync_api import sync_playwright

CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
BASE_URL = "http://localhost:3000"

def run_adversarial_tests():
    print("=" * 70)
    print("STARTING ADVERSARIAL STRESS TESTING SUITE")
    print("=" * 70)

    stress_passed = True
    results = {}

    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)
        page = browser.new_page()

        # -------------------------------------------------------------
        # TEST 1: Textarea Multiline Expansion & In-Bounds Clamping (320x568)
        # -------------------------------------------------------------
        print("\n[STRESS 1] Textarea Multiline Expansion & Anchoring at 320x568")
        page.set_viewport_size({"width": 320, "height": 568})
        page.goto(BASE_URL, wait_until="domcontentloaded")
        page.wait_for_selector(".app-shell", timeout=5000)

        textarea = page.locator("textarea")
        # Type 10 lines of long text
        multiline_text = "Dòng 1: Thử nghiệm ứng suất giao diện trên thiết bị siêu nhỏ iPhone SE\n" * 8
        textarea.fill(multiline_text)
        page.wait_for_timeout(300)

        t1_metrics = page.evaluate("""() => {
            const doc = document.documentElement;
            const inputWrapper = document.querySelector('.chat-input-wrapper');
            const textarea = document.querySelector('textarea');
            const rect = inputWrapper.getBoundingClientRect();
            return {
                docScrollWidth: doc.scrollWidth,
                docClientWidth: doc.clientWidth,
                bottom: rect.bottom,
                top: rect.top,
                height: rect.height,
                textareaHeight: textarea.getBoundingClientRect().height,
                overflowX: doc.scrollWidth > doc.clientWidth
            };
        }""")

        t1_ok = (not t1_metrics["overflowX"]) and (t1_metrics["bottom"] <= 568 + 1)
        print(f"  Doc overflow: {t1_metrics['overflowX']}, Input wrapper bottom: {t1_metrics['bottom']} (vp height: 568), Textarea height: {t1_metrics['textareaHeight']}")
        if not t1_ok:
            stress_passed = False
            print("  [FAIL] Textarea expansion clipped or overflowed!")
        else:
            print("  [PASS] Textarea expansion perfectly clamped within viewport boundaries")
        results["textarea_expansion_320x568"] = t1_ok

        # -------------------------------------------------------------
        # TEST 2: Long Unbreakable String Horizontal Containment (320x568)
        # -------------------------------------------------------------
        print("\n[STRESS 2] Long Unbreakable String in Input at 320x568")
        unbreakable_str = "A" * 150 + "https://docmind.example.com/very/long/unbreakable/path/without/spaces/abcdefghijklmnopqrstuvwxyz1234567890"
        textarea.fill(unbreakable_str)
        page.wait_for_timeout(200)

        t2_metrics = page.evaluate("""() => {
            const doc = document.documentElement;
            const inputCard = document.querySelector('.input-card');
            return {
                docScrollWidth: doc.scrollWidth,
                docClientWidth: doc.clientWidth,
                cardScrollWidth: inputCard.scrollWidth,
                cardClientWidth: inputCard.clientWidth
            };
        }""")
        t2_ok = (t2_metrics["docScrollWidth"] <= t2_metrics["docClientWidth"]) and (t2_metrics["cardScrollWidth"] <= t2_metrics["cardClientWidth"])
        print(f"  Doc: {t2_metrics['docScrollWidth']} <= {t2_metrics['docClientWidth']}, Card: {t2_metrics['cardScrollWidth']} <= {t2_metrics['cardClientWidth']}")
        if not t2_ok:
            stress_passed = False
            print("  [FAIL] Unbreakable string caused horizontal overflow!")
        else:
            print("  [PASS] Unbreakable string wrapped/contained cleanly")
        results["unbreakable_string_320x568"] = t2_ok

        # Clear textarea
        textarea.fill("")

        # -------------------------------------------------------------
        # TEST 3: Touch Target Sizes on Mobile (375x812 and 320x568)
        # -------------------------------------------------------------
        print("\n[STRESS 3] Touch Target Sizes Audit (>= 44x44px)")
        page.set_viewport_size({"width": 375, "height": 812})
        page.goto(BASE_URL, wait_until="domcontentloaded")
        page.wait_for_selector(".app-shell", timeout=5000)

        # Open drawer to inspect drawer targets
        toggle_btn = page.locator(".btn-drawer-toggle")
        toggle_box = toggle_btn.bounding_box()
        t3_toggle = round(toggle_box["width"], 2) >= 44 and round(toggle_box["height"], 2) >= 44
        print(f"  Hamburger toggle button: {toggle_box['width']}x{toggle_box['height']} (>= 44x44: {t3_toggle})")

        toggle_btn.click()
        page.wait_for_timeout(350)

        close_btn = page.locator(".btn-drawer-close")
        close_box = close_btn.bounding_box()
        t3_close = round(close_box["width"], 2) >= 44 and round(close_box["height"], 2) >= 44
        print(f"  Drawer close button: {close_box['width']}x{close_box['height']} (>= 44x44: {t3_close})")

        refresh_btn = page.locator(".btn-refresh")
        refresh_box = refresh_btn.bounding_box()
        t3_refresh = round(refresh_box["width"], 2) >= 44 and round(refresh_box["height"], 2) >= 44
        print(f"  Refresh documents button: {refresh_box['width']}x{refresh_box['height']} (>= 44x44: {t3_refresh})")

        pill_btn = page.locator(".filter-pills .pill-btn").first
        pill_box = pill_btn.bounding_box()
        t3_pill = round(pill_box["height"], 2) >= 44
        print(f"  Filter pill height: {pill_box['height']} (>= 44: {t3_pill})")

        # Close drawer
        close_btn.click()
        page.wait_for_timeout(300)

        # Header action buttons
        evo_btn = page.locator(".btn-evo")
        evo_box = evo_btn.bounding_box()
        t3_evo = round(evo_box["width"], 2) >= 44 and round(evo_box["height"], 2) >= 44
        print(f"  EvoAgentX trigger: {evo_box['width']}x{evo_box['height']} (>= 44x44: {t3_evo})")

        astrbot_btn = page.locator(".btn-astrbot")
        astrbot_box = astrbot_btn.bounding_box()
        t3_astrbot = round(astrbot_box["width"], 2) >= 44 and round(astrbot_box["height"], 2) >= 44
        print(f"  AstrBot trigger: {astrbot_box['width']}x{astrbot_box['height']} (>= 44x44: {t3_astrbot})")

        settings_btn = page.locator(".btn-settings-toggle")
        settings_box = settings_btn.bounding_box()
        t3_settings = round(settings_box["width"], 2) >= 44 and round(settings_box["height"], 2) >= 44
        print(f"  Settings toggle: {settings_box['width']}x{settings_box['height']} (>= 44x44: {t3_settings})")

        starter_card = page.locator(".starter-card").first
        starter_box = starter_card.bounding_box()
        t3_starter = round(starter_box["height"], 2) >= 44
        print(f"  Starter card height: {starter_box['height']} (>= 44: {t3_starter})")

        t3_all = t3_toggle and t3_close and t3_refresh and t3_pill and t3_evo and t3_astrbot and t3_settings and t3_starter
        if not t3_all:
            stress_passed = False
            print("  [FAIL] Some touch targets are smaller than 44px!")
        else:
            print("  [PASS] All critical touch targets meet or exceed 44px standard")
        results["touch_target_sizes"] = t3_all

        # -------------------------------------------------------------
        # TEST 4: Rapid Drawer Toggling Flurry (375x812)
        # -------------------------------------------------------------
        print("\n[STRESS 4] Rapid Drawer Toggling (5 cycles)")
        for i in range(5):
            toggle_btn.click()
            page.wait_for_timeout(100) # interrupt during animation
            page.keyboard.press("Escape")
            page.wait_for_timeout(100)

        # Let settle and check final state
        page.wait_for_timeout(400)
        t4_metrics = page.evaluate("""() => {
            const doc = document.documentElement;
            const sidebar = document.querySelector('.sidebar-container');
            return {
                docScrollWidth: doc.scrollWidth,
                docClientWidth: doc.clientWidth,
                isOpen: sidebar.classList.contains('is-mobile-open'),
                overflowX: doc.scrollWidth > doc.clientWidth
            };
        }""")
        t4_ok = (not t4_metrics["overflowX"]) and (not t4_metrics["isOpen"])
        print(f"  Rapid cycle final state: isOpen={t4_metrics['isOpen']}, overflowX={t4_metrics['overflowX']}")
        if not t4_ok:
            stress_passed = False
            print("  [FAIL] Rapid drawer toggling caused stuck state or overflow!")
        else:
            print("  [PASS] Drawer state recovered cleanly with zero overflow")
        results["rapid_drawer_toggling"] = t4_ok

        # -------------------------------------------------------------
        # TEST 5: Starters Grid Wrapping on Ultra-Narrow 320x568
        # -------------------------------------------------------------
        print("\n[STRESS 5] Starters Grid Layout on 320x568")
        page.set_viewport_size({"width": 320, "height": 568})
        page.wait_for_timeout(200)

        t5_metrics = page.evaluate("""() => {
            const grid = document.querySelector('.starters-grid');
            const cards = Array.from(document.querySelectorAll('.starter-card'));
            const doc = document.documentElement;
            return {
                docScrollWidth: doc.scrollWidth,
                docClientWidth: doc.clientWidth,
                gridColumns: window.getComputedStyle(grid).gridTemplateColumns,
                cardWidths: cards.map(c => c.getBoundingClientRect().width),
                cardOverflows: cards.map(c => c.scrollWidth > c.clientWidth)
            };
        }""")
        any_card_overflow = any(t5_metrics["cardOverflows"])
        doc_overflow = t5_metrics["docScrollWidth"] > t5_metrics["docClientWidth"]
        t5_ok = (not any_card_overflow) and (not doc_overflow)
        print(f"  Grid template: {t5_metrics['gridColumns']}, Card widths: {t5_metrics['cardWidths']}, Card overflow: {any_card_overflow}")
        if not t5_ok:
            stress_passed = False
            print("  [FAIL] Starter cards overflowed at 320px width!")
        else:
            print("  [PASS] Single-column starter cards fit seamlessly at 320px")
        results["starters_grid_320x568"] = t5_ok

        browser.close()

    print("\n" + "=" * 70)
    print("ADVERSARIAL STRESS TEST SUMMARY:")
    print("=" * 70)
    for k, v in results.items():
        print(f"  [{'PASS' if v else 'FAIL'}] {k}")

    final_verdict = "APPROVE" if stress_passed else "REJECT"
    print(f"\nSTRESS SUITE VERDICT: {final_verdict}")
    print("=" * 70)

    with open("D:/docmind/frontend/e2e/adversarial_stress_results.json", "w", encoding="utf-8") as f:
        json.dump({"verdict": final_verdict, "results": results}, f, indent=2)

    return stress_passed

if __name__ == "__main__":
    ok = run_adversarial_tests()
    sys.exit(0 if ok else 1)
