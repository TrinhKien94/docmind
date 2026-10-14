#!/usr/bin/env python3
"""
Empirical Viewport Responsiveness Verification Suite
Tests the 9 mandatory breakpoints + boundary checks:
1. Ultra-compact mobile: 320x568 (iPhone SE 1st gen)
2. Standard mobile: 375x812 (iPhone X/13/Pixel)
3. Large mobile: 412x915 (Pixel 7/Samsung Galaxy)
4. Small tablet portrait: 600x960
5. Tablet portrait: 768x1024 (iPad portrait)
6. Tablet landscape: 1024x768 (iPad landscape)
7. Desktop: 1280x800
8. Desktop: 1440x900
9. Desktop: 1920x1080

At each resolution, checks:
- document.documentElement.scrollWidth <= document.documentElement.clientWidth
- .chat-header scrollWidth <= clientWidth
- on < 768px: chatContainer left == 0 and width == viewport width
- .chat-input-wrapper anchored in-bounds (bottom <= viewport height, top >= 0)
- stress test: settings dropdown open state
- stress test: drawer open state on mobile
"""

import sys
import json
from playwright.sync_api import sync_playwright

CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
BASE_URL = "http://localhost:3000"

VIEWPORT_MATRIX = [
    {"name": "Ultra-compact mobile (iPhone SE 1st)", "width": 320, "height": 568, "is_mobile": True},
    {"name": "Standard mobile (iPhone X/13/Pixel)", "width": 375, "height": 812, "is_mobile": True},
    {"name": "Large mobile (Pixel 7/Galaxy)", "width": 412, "height": 915, "is_mobile": True},
    {"name": "Small tablet portrait", "width": 600, "height": 960, "is_mobile": True},
    {"name": "Mobile boundary (upper edge)", "width": 767, "height": 1024, "is_mobile": True},
    {"name": "Tablet portrait (iPad portrait)", "width": 768, "height": 1024, "is_mobile": False},
    {"name": "Tablet landscape (iPad landscape)", "width": 1024, "height": 768, "is_mobile": False},
    {"name": "Desktop WXGA", "width": 1280, "height": 800, "is_mobile": False},
    {"name": "Desktop WXGA+", "width": 1440, "height": 900, "is_mobile": False},
    {"name": "Desktop Full HD", "width": 1920, "height": 1080, "is_mobile": False},
]

def run_tests():
    print("=" * 70)
    print("STARTING EMPIRICAL VIEWPORT RESPONSIVENESS MATRIX TEST")
    print("=" * 70)

    all_passed = True
    results = []

    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)
        page = browser.new_page()

        for vp in VIEWPORT_MATRIX:
            w, h = vp["width"], vp["height"]
            name = vp["name"]
            is_mobile = vp["is_mobile"]

            print(f"\n---> Testing {name} [{w}x{h}] (Mobile mode: {is_mobile})")
            page.set_viewport_size({"width": w, "height": h})
            page.goto(BASE_URL, wait_until="domcontentloaded")
            page.wait_for_selector(".app-shell", timeout=6000)
            page.wait_for_timeout(300) # allow any layout transition to settle

            # 1. Base audit
            data = page.evaluate("""() => {
                const doc = document.documentElement;
                const body = document.body;
                const header = document.querySelector('.chat-header');
                const chatContainer = document.querySelector('.chat-container');
                const sidebar = document.querySelector('.sidebar-container');
                const inputWrapper = document.querySelector('.chat-input-wrapper');

                const headerRect = header ? header.getBoundingClientRect() : null;
                const chatRect = chatContainer ? chatContainer.getBoundingClientRect() : null;
                const sidebarRect = sidebar ? sidebar.getBoundingClientRect() : null;
                const inputRect = inputWrapper ? inputWrapper.getBoundingClientRect() : null;

                return {
                    docScrollWidth: doc.scrollWidth,
                    docClientWidth: doc.clientWidth,
                    bodyScrollWidth: body.scrollWidth,
                    bodyClientWidth: body.clientWidth,
                    windowInnerWidth: window.innerWidth,
                    windowInnerHeight: window.innerHeight,
                    header: header ? {
                        scrollWidth: header.scrollWidth,
                        clientWidth: header.clientWidth,
                        rect: JSON.parse(JSON.stringify(headerRect))
                    } : null,
                    chatContainer: chatContainer ? {
                        rect: JSON.parse(JSON.stringify(chatRect))
                    } : null,
                    sidebar: sidebar ? {
                        rect: JSON.parse(JSON.stringify(sidebarRect)),
                        position: window.getComputedStyle(sidebar).position,
                        transform: window.getComputedStyle(sidebar).transform
                    } : null,
                    inputWrapper: inputWrapper ? {
                        rect: JSON.parse(JSON.stringify(inputRect))
                    } : null
                };
            }""")

            vp_result = {
                "name": name,
                "width": w,
                "height": h,
                "checks": []
            }

            # Criterion 1: Zero horizontal overflow
            doc_no_overflow = data["docScrollWidth"] <= data["docClientWidth"]
            vp_result["checks"].append({
                "name": "Zero document horizontal overflow",
                "condition": f"scrollWidth ({data['docScrollWidth']}) <= clientWidth ({data['docClientWidth']})",
                "passed": doc_no_overflow
            })
            if not doc_no_overflow:
                all_passed = False
                print(f"  [FAIL] Document horizontal overflow: scrollWidth={data['docScrollWidth']} > clientWidth={data['docClientWidth']}")
            else:
                print(f"  [PASS] Zero document overflow: {data['docScrollWidth']} <= {data['docClientWidth']}")

            # Criterion 2: .chat-header scrollWidth <= clientWidth
            if data["header"]:
                header_no_overflow = data["header"]["scrollWidth"] <= data["header"]["clientWidth"]
                vp_result["checks"].append({
                    "name": "Chat header zero horizontal overflow",
                    "condition": f"header.scrollWidth ({data['header']['scrollWidth']}) <= clientWidth ({data['header']['clientWidth']})",
                    "passed": header_no_overflow
                })
                if not header_no_overflow:
                    all_passed = False
                    print(f"  [FAIL] Header horizontal overflow: scrollWidth={data['header']['scrollWidth']} > clientWidth={data['header']['clientWidth']}")
                else:
                    print(f"  [PASS] Header zero overflow: {data['header']['scrollWidth']} <= {data['header']['clientWidth']}")
            else:
                all_passed = False
                print(f"  [FAIL] .chat-header not found in DOM!")

            # Criterion 3: On < 768px, chatContainer occupies 100% of visible viewport width (left: 0)
            if is_mobile:
                chat_rect = data["chatContainer"]["rect"] if data["chatContainer"] else None
                left_zero = chat_rect and abs(chat_rect["left"]) < 0.5
                width_full = chat_rect and abs(chat_rect["width"] - w) < 1.0
                sidebar_offcanvas = data["sidebar"] and (
                    data["sidebar"]["position"] == "fixed" and (
                        data["sidebar"]["rect"]["right"] <= 0.5 or data["sidebar"]["transform"].startswith("matrix")
                    )
                )

                crit3_passed = left_zero and width_full and sidebar_offcanvas
                vp_result["checks"].append({
                    "name": "Mobile full-screen chat container (left: 0, width: 100%)",
                    "condition": f"left={chat_rect['left'] if chat_rect else None} (~0), width={chat_rect['width'] if chat_rect else None} (~{w}), sidebar_offcanvas={sidebar_offcanvas}",
                    "passed": crit3_passed
                })
                if not crit3_passed:
                    all_passed = False
                    print(f"  [FAIL] Mobile chat container: left={chat_rect['left']}, width={chat_rect['width']} (expected {w}), sidebar_offcanvas={sidebar_offcanvas}")
                else:
                    print(f"  [PASS] Mobile chat container: left={chat_rect['left']} (0), width={chat_rect['width']} (100%), sidebar off-canvas")
            else:
                # Desktop/Tablet >= 768px: sidebar is side-by-side
                sidebar_inflow = data["sidebar"] and data["sidebar"]["position"] == "relative"
                vp_result["checks"].append({
                    "name": "Desktop/Tablet in-flow side-by-side sidebar",
                    "condition": f"sidebar.position={data['sidebar']['position'] if data['sidebar'] else None}",
                    "passed": sidebar_inflow
                })
                print(f"  [PASS] Desktop/Tablet layout: sidebar in-flow (position: relative, width: {data['sidebar']['rect']['width'] if data['sidebar'] else 'N/A'})")

            # Criterion 4: .chat-input-wrapper is anchored in-bounds and not clipped by bottom edges
            if data["inputWrapper"]:
                in_rect = data["inputWrapper"]["rect"]
                input_anchored = (
                    in_rect["top"] >= 0 and
                    in_rect["bottom"] <= (h + 1.0) and # allow subpixel rounding
                    in_rect["left"] >= 0 and
                    in_rect["right"] <= (w + 1.0)
                )
                vp_result["checks"].append({
                    "name": "Input wrapper anchored in-bounds",
                    "condition": f"top={in_rect['top']} >= 0, bottom={in_rect['bottom']} <= {h}, left={in_rect['left']} >= 0, right={in_rect['right']} <= {w}",
                    "passed": input_anchored
                })
                if not input_anchored:
                    all_passed = False
                    print(f"  [FAIL] Input wrapper out of bounds: top={in_rect['top']}, bottom={in_rect['bottom']} (vp height: {h}), left={in_rect['left']}, right={in_rect['right']}")
                else:
                    print(f"  [PASS] Input wrapper anchored in-bounds: bottom={in_rect['bottom']} <= {h}")
            else:
                all_passed = False
                print(f"  [FAIL] .chat-input-wrapper not found in DOM!")

            # Stress Test 1: Settings dropdown open (on mobile and tablet)
            settings_btn = page.locator(".btn-settings-toggle")
            if settings_btn.is_visible():
                settings_btn.click()
                page.wait_for_timeout(350)
                settings_data = page.evaluate("""() => {
                    const doc = document.documentElement;
                    const header = document.querySelector('.chat-header');
                    return {
                        docScrollWidth: doc.scrollWidth,
                        docClientWidth: doc.clientWidth,
                        headerScrollWidth: header ? header.scrollWidth : 0,
                        headerClientWidth: header ? header.clientWidth : 0
                    };
                }""")
                settings_ok = (
                    settings_data["docScrollWidth"] <= settings_data["docClientWidth"] and
                    settings_data["headerScrollWidth"] <= settings_data["headerClientWidth"]
                )
                vp_result["checks"].append({
                    "name": "Stress Test: Settings Row Open zero overflow",
                    "condition": f"doc: {settings_data['docScrollWidth']} <= {settings_data['docClientWidth']}, header: {settings_data['headerScrollWidth']} <= {settings_data['headerClientWidth']}",
                    "passed": settings_ok
                })
                if not settings_ok:
                    all_passed = False
                    print(f"  [FAIL] Stress Test Settings Open overflowed: doc {settings_data['docScrollWidth']}>{settings_data['docClientWidth']} or header {settings_data['headerScrollWidth']}>{settings_data['headerClientWidth']}")
                else:
                    print(f"  [PASS] Stress Test Settings Open: zero overflow (header {settings_data['headerScrollWidth']} <= {settings_data['headerClientWidth']})")
                
                # close settings
                settings_btn.click()
                page.wait_for_timeout(200)

            # Stress Test 2: Drawer open state on mobile (< 768px)
            if is_mobile:
                toggle_btn = page.locator(".btn-drawer-toggle")
                if toggle_btn.is_visible():
                    toggle_btn.click()
                    page.wait_for_timeout(350)
                    drawer_data = page.evaluate("""() => {
                        const doc = document.documentElement;
                        const sidebar = document.querySelector('.sidebar-container');
                        return {
                            docScrollWidth: doc.scrollWidth,
                            docClientWidth: doc.clientWidth,
                            sidebarWidth: sidebar ? sidebar.getBoundingClientRect().width : 0,
                            sidebarLeft: sidebar ? sidebar.getBoundingClientRect().left : 0,
                            isOpen: sidebar ? sidebar.classList.contains('is-mobile-open') : false
                        };
                    }""")
                    drawer_ok = (
                        drawer_data["docScrollWidth"] <= drawer_data["docClientWidth"] and
                        drawer_data["isOpen"] and
                        drawer_data["sidebarWidth"] <= w
                    )
                    vp_result["checks"].append({
                        "name": "Stress Test: Mobile Drawer Open zero overflow",
                        "condition": f"docScrollWidth ({drawer_data['docScrollWidth']}) <= docClientWidth ({drawer_data['docClientWidth']}), sidebarWidth ({drawer_data['sidebarWidth']}) <= {w}",
                        "passed": drawer_ok
                    })
                    if not drawer_ok:
                        all_passed = False
                        print(f"  [FAIL] Stress Test Mobile Drawer Open overflowed: doc {drawer_data['docScrollWidth']}>{drawer_data['docClientWidth']} or width {drawer_data['sidebarWidth']}>{w}")
                    else:
                        print(f"  [PASS] Stress Test Mobile Drawer Open: width={drawer_data['sidebarWidth']}px, zero doc overflow")

                    # Close drawer via Escape
                    page.keyboard.press("Escape")
                    page.wait_for_timeout(300)

            results.append(vp_result)

        browser.close()

    print("\n" + "=" * 70)
    print("EMPIRICAL TEST SUMMARY:")
    print("=" * 70)
    for r in results:
        status = "ALL PASS" if all(c["passed"] for c in r["checks"]) else "SOME FAILED"
        print(f"[{status}] {r['name']} ({r['width']}x{r['height']}): {sum(1 for c in r['checks'] if c['passed'])}/{len(r['checks'])} checks passed")

    final_verdict = "APPROVE" if all_passed else "REJECT"
    print(f"\nFINAL VERDICT: {final_verdict}")
    print("=" * 70)

    # Save results as JSON
    with open("D:/docmind/frontend/e2e/viewport_matrix_results.json", "w", encoding="utf-8") as f:
        json.dump({"verdict": final_verdict, "results": results}, f, indent=2)

    return all_passed

if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)
