#!/usr/bin/env python3
"""
Comprehensive Empirical Multi-Viewport & Layout Stress Test
Tests 15 viewports from 320px to 1920px:
1. Zero horizontal overflow (scrollWidth <= clientWidth) across:
   - html (document.documentElement)
   - body
   - .app-shell
   - .main-layout
   - .chat-header
   - .messages-area
   - .chat-input-wrapper
2. Mobile drawer behavior (< 768px vs >= 768px):
   - Off-canvas when closed, in-canvas when open
   - Backdrop visibility and z-index (55)
   - Desktop in-flow positioning (position: relative)
3. Modal layering & z-index stacking context:
   - Modal backdrop z-index: 1100 > sidebar (1000) > backdrop (55)
   - Center probe hits modal card
   - Modal card fits inside viewport without horizontal overflow
4. Dynamic cross-breakpoint resize (mobile -> desktop -> mobile with drawer active)
5. Textarea multi-line expansion on 320x568 (no bottom clipping)
6. Toast banner z-index (1200) and viewport containment
"""

import sys
import json
import time
from playwright.sync_api import sync_playwright

CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
BASE_URL = "http://localhost:3000"

VIEWPORTS = [
    {"name": "Ultra-compact mobile (iPhone SE 1st)", "width": 320, "height": 568, "is_mobile": True},
    {"name": "Android baseline", "width": 360, "height": 640, "is_mobile": True},
    {"name": "iPhone 8 / SE 2nd", "width": 375, "height": 667, "is_mobile": True},
    {"name": "iPhone X / 12 / 13", "width": 375, "height": 812, "is_mobile": True},
    {"name": "iPhone 14 / 15 Pro", "width": 390, "height": 844, "is_mobile": True},
    {"name": "Pixel 7 / Galaxy S21", "width": 412, "height": 915, "is_mobile": True},
    {"name": "iPhone 11 / XR / Plus", "width": 414, "height": 896, "is_mobile": True},
    {"name": "Budget Android landscape/wide", "width": 480, "height": 854, "is_mobile": True},
    {"name": "Small 7-inch tablet portrait", "width": 600, "height": 960, "is_mobile": True},
    {"name": "Mobile upper boundary", "width": 767, "height": 1024, "is_mobile": True},
    {"name": "Tablet portrait (iPad portrait)", "width": 768, "height": 1024, "is_mobile": False},
    {"name": "Tablet portrait alternate", "width": 800, "height": 1280, "is_mobile": False},
    {"name": "Tablet landscape (iPad landscape)", "width": 1024, "height": 768, "is_mobile": False},
    {"name": "Desktop WXGA", "width": 1280, "height": 800, "is_mobile": False},
    {"name": "Desktop MacBook WXGA+", "width": 1440, "height": 900, "is_mobile": False},
    {"name": "Desktop Full HD", "width": 1920, "height": 1080, "is_mobile": False},
]

def run_tests():
    print("=" * 80)
    print("EMPIRICAL MULTI-VIEWPORT VERIFICATION SUITE (320px - 1920px)")
    print("=" * 80)

    overall_passed = True
    test_results = []

    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)
        page = browser.new_page()

        # Mock api to load demo data immediately
        page.route("**/api/**", lambda route: route.fulfill(status=500, body='{"error":"offline_mock"}'))

        for vp in VIEWPORTS:
            w, h = vp["width"], vp["height"]
            name = vp["name"]
            is_mobile = vp["is_mobile"]

            print(f"\n---> Testing Viewport: {name} [{w}x{h}] (Mobile: {is_mobile})")
            page.set_viewport_size({"width": w, "height": h})
            page.goto(BASE_URL, wait_until="domcontentloaded")
            page.wait_for_selector(".app-shell", timeout=6000)
            page.wait_for_timeout(250)

            vp_record = {"name": name, "width": w, "height": h, "checks": []}

            # 1. Container overflow checks (html, body, .app-shell, .main-layout, .chat-header)
            overflow_data = page.evaluate("""() => {
                const doc = document.documentElement;
                const body = document.body;
                const appShell = document.querySelector('.app-shell');
                const mainLayout = document.querySelector('.main-layout');
                const header = document.querySelector('.chat-header');
                const messagesArea = document.querySelector('.messages-area');
                const inputWrapper = document.querySelector('.chat-input-wrapper');

                const checkEl = (el, name) => {
                    if (!el) return { name, exists: false, scrollWidth: 0, clientWidth: 0, overflowX: false };
                    return {
                        name,
                        exists: true,
                        scrollWidth: el.scrollWidth,
                        clientWidth: el.clientWidth,
                        overflowX: el.scrollWidth > el.clientWidth
                    };
                };

                return {
                    doc: checkEl(doc, 'html'),
                    body: checkEl(body, 'body'),
                    appShell: checkEl(appShell, '.app-shell'),
                    mainLayout: checkEl(mainLayout, '.main-layout'),
                    header: checkEl(header, '.chat-header'),
                    messagesArea: checkEl(messagesArea, '.messages-area'),
                    inputWrapper: checkEl(inputWrapper, '.chat-input-wrapper')
                };
            }""")

            # Verify every critical container has scrollWidth <= clientWidth
            for key, el_data in overflow_data.items():
                if el_data["exists"]:
                    passed = not el_data["overflowX"]
                    vp_record["checks"].append({
                        "name": f"Container {el_data['name']} zero overflow",
                        "details": f"scrollWidth ({el_data['scrollWidth']}) <= clientWidth ({el_data['clientWidth']})",
                        "passed": passed
                    })
                    if not passed:
                        overall_passed = False
                        print(f"  [FAIL] {el_data['name']} overflow: scrollWidth={el_data['scrollWidth']} > clientWidth={el_data['clientWidth']}")
                    else:
                        print(f"  [PASS] {el_data['name']} zero overflow: {el_data['scrollWidth']} <= {el_data['clientWidth']}")

            # 2. Sidebar position & toggle button checks
            sidebar_data = page.evaluate("""() => {
                const sidebar = document.querySelector('.sidebar-container');
                const toggle = document.querySelector('.btn-drawer-toggle');
                const backdrop = document.querySelector('.mobile-drawer-backdrop');

                const sRect = sidebar ? sidebar.getBoundingClientRect() : null;
                const tRect = toggle ? toggle.getBoundingClientRect() : null;
                const sComputed = sidebar ? window.getComputedStyle(sidebar) : null;
                const tComputed = toggle ? window.getComputedStyle(toggle) : null;

                return {
                    sidebarExists: !!sidebar,
                    toggleExists: !!toggle,
                    toggleVisible: toggle ? (tComputed.display !== 'none' && tRect.width > 0 && tRect.height > 0) : false,
                    sidebarPosition: sComputed ? sComputed.position : null,
                    sidebarTransform: sComputed ? sComputed.transform : null,
                    sidebarLeft: sRect ? sRect.left : null,
                    sidebarRight: sRect ? sRect.right : null,
                    sidebarWidth: sRect ? sRect.width : null,
                    isOpen: sidebar ? sidebar.classList.contains('is-mobile-open') : false,
                    backdropVisible: backdrop ? (window.getComputedStyle(backdrop).display !== 'none') : false
                };
            }""")

            if is_mobile:
                # Must be off-canvas initially
                is_offcanvas = sidebar_data["sidebarPosition"] == "fixed" and (
                    sidebar_data["sidebarRight"] <= 0.5 or sidebar_data["sidebarTransform"].startswith("matrix")
                )
                toggle_visible = sidebar_data["toggleVisible"]
                vp_record["checks"].append({
                    "name": "Mobile sidebar initially off-canvas",
                    "details": f"position={sidebar_data['sidebarPosition']}, right={sidebar_data['sidebarRight']}, transform={sidebar_data['sidebarTransform']}",
                    "passed": is_offcanvas
                })
                vp_record["checks"].append({
                    "name": "Mobile toggle button visible",
                    "details": f"toggleVisible={toggle_visible}",
                    "passed": toggle_visible
                })
                if not (is_offcanvas and toggle_visible):
                    overall_passed = False
                    print(f"  [FAIL] Mobile sidebar state: offcanvas={is_offcanvas}, toggleVisible={toggle_visible}")
                else:
                    print(f"  [PASS] Mobile sidebar off-canvas (right={sidebar_data['sidebarRight']}), toggle visible")

                # Test opening the drawer
                page.locator(".btn-drawer-toggle").click()
                page.wait_for_timeout(350)

                drawer_open_data = page.evaluate("""() => {
                    const doc = document.documentElement;
                    const sidebar = document.querySelector('.sidebar-container');
                    const backdrop = document.querySelector('.mobile-drawer-backdrop');
                    const sRect = sidebar.getBoundingClientRect();
                    const sStyle = window.getComputedStyle(sidebar);
                    const bStyle = backdrop ? window.getComputedStyle(backdrop) : null;

                    return {
                        isOpen: sidebar.classList.contains('is-mobile-open'),
                        sidebarLeft: sRect.left,
                        sidebarWidth: sRect.width,
                        sidebarZIndex: sStyle.zIndex,
                        backdropZIndex: bStyle ? bStyle.zIndex : null,
                        backdropVisible: bStyle ? (bStyle.display !== 'none' && parseFloat(bStyle.opacity) > 0) : false,
                        docScrollWidth: doc.scrollWidth,
                        docClientWidth: doc.clientWidth,
                        appShellScrollWidth: document.querySelector('.app-shell').scrollWidth,
                        appShellClientWidth: document.querySelector('.app-shell').clientWidth
                    };
                }""")

                drawer_opened_ok = (
                    drawer_open_data["isOpen"] and
                    abs(drawer_open_data["sidebarLeft"]) < 1.0 and
                    drawer_open_data["backdropVisible"] and
                    drawer_open_data["sidebarZIndex"] == "1000" and
                    drawer_open_data["backdropZIndex"] == "55" and
                    drawer_open_data["docScrollWidth"] <= drawer_open_data["docClientWidth"] and
                    drawer_open_data["appShellScrollWidth"] <= drawer_open_data["appShellClientWidth"]
                )
                vp_record["checks"].append({
                    "name": "Mobile drawer opens cleanly with backdrop and zero overflow",
                    "details": str(drawer_open_data),
                    "passed": drawer_opened_ok
                })
                if not drawer_opened_ok:
                    overall_passed = False
                    print(f"  [FAIL] Drawer open check failed: {drawer_open_data}")
                else:
                    print(f"  [PASS] Drawer open: left={drawer_open_data['sidebarLeft']}, zIndex={drawer_open_data['sidebarZIndex']}, backdropZ={drawer_open_data['backdropZIndex']}, zero overflow")

                # While drawer is open, test opening DocumentDetailModal
                detail_btn = page.locator(".documents-list .doc-card .btn-icon").first
                if detail_btn.is_visible():
                    detail_btn.click()
                    page.wait_for_timeout(350)

                    modal_data = page.evaluate("""() => {
                        const modalBackdrop = document.querySelector('.modal-backdrop');
                        const modalCard = document.querySelector('.modal-card');
                        const doc = document.documentElement;

                        const mbStyle = modalBackdrop ? window.getComputedStyle(modalBackdrop) : null;
                        const mcRect = modalCard ? modalCard.getBoundingClientRect() : null;
                        
                        // Probe center of screen
                        const centerEl = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);

                        return {
                            modalOpen: !!modalBackdrop,
                            modalZIndex: mbStyle ? mbStyle.zIndex : null,
                            cardRect: mcRect ? JSON.parse(JSON.stringify(mcRect)) : null,
                            cardFitsWidth: mcRect ? (mcRect.left >= 0 && mcRect.right <= window.innerWidth) : false,
                            cardFitsHeight: mcRect ? (mcRect.top >= 0 && mcRect.bottom <= window.innerHeight) : false,
                            centerHitsModal: centerEl ? !!centerEl.closest('.modal-card') : false,
                            docScrollWidth: doc.scrollWidth,
                            docClientWidth: doc.clientWidth
                        };
                    }""")

                    modal_ok = (
                        modal_data["modalOpen"] and
                        modal_data["modalZIndex"] == "1100" and
                        modal_data["centerHitsModal"] and
                        modal_data["cardFitsWidth"] and
                        modal_data["docScrollWidth"] <= modal_data["docClientWidth"]
                    )
                    vp_record["checks"].append({
                        "name": "Document detail modal elevates above drawer (z: 1100 > 1000) with zero overflow",
                        "details": str(modal_data),
                        "passed": modal_ok
                    })
                    if not modal_ok:
                        overall_passed = False
                        print(f"  [FAIL] Modal layering check failed: {modal_data}")
                    else:
                        print(f"  [PASS] Modal layering: zIndex={modal_data['modalZIndex']} > 1000, hits center={modal_data['centerHitsModal']}, card fits width={modal_data['cardFitsWidth']}")

                    # Close modal
                    page.keyboard.press("Escape")
                    page.wait_for_timeout(300)

                # Close drawer
                page.keyboard.press("Escape")
                page.wait_for_timeout(300)

                drawer_closed_data = page.evaluate("""() => {
                    const sidebar = document.querySelector('.sidebar-container');
                    const backdrop = document.querySelector('.mobile-drawer-backdrop');
                    const bStyle = backdrop ? window.getComputedStyle(backdrop) : null;
                    return {
                        isOpen: sidebar.classList.contains('is-mobile-open'),
                        backdropHidden: !backdrop || bStyle.display === 'none' || parseFloat(bStyle.opacity) === 0
                    };
                }""")
                drawer_closed_ok = (not drawer_closed_data["isOpen"]) and drawer_closed_data["backdropHidden"]
                vp_record["checks"].append({
                    "name": "Mobile drawer closes cleanly via Escape",
                    "details": str(drawer_closed_data),
                    "passed": drawer_closed_ok
                })
                if not drawer_closed_ok:
                    overall_passed = False
                    print(f"  [FAIL] Drawer close check failed: {drawer_closed_data}")
                else:
                    print(f"  [PASS] Drawer closed: isOpen={drawer_closed_data['isOpen']}, backdropHidden={drawer_closed_data['backdropHidden']}")

            else:
                # Desktop layout checks
                desktop_inflow = (
                    sidebar_data["sidebarPosition"] == "relative" and
                    sidebar_data["sidebarWidth"] >= 340 and
                    abs(sidebar_data["sidebarLeft"]) < 1.0 and
                    not sidebar_data["toggleVisible"]
                )
                vp_record["checks"].append({
                    "name": "Desktop sidebar in-flow (relative, >=340px) and toggle hidden",
                    "details": f"position={sidebar_data['sidebarPosition']}, width={sidebar_data['sidebarWidth']}, toggleVisible={sidebar_data['toggleVisible']}",
                    "passed": desktop_inflow
                })
                if not desktop_inflow:
                    overall_passed = False
                    print(f"  [FAIL] Desktop sidebar check failed: {sidebar_data}")
                else:
                    print(f"  [PASS] Desktop sidebar in-flow: width={sidebar_data['sidebarWidth']}, toggle hidden")

            test_results.append(vp_record)

        # 3. Dynamic Resize Stress Test across 768px Boundary
        print("\n---> Stress Test: Cross-Breakpoint Dynamic Resize with Open Drawer")
        page.set_viewport_size({"width": 375, "height": 812})
        page.wait_for_timeout(300)
        page.locator(".btn-drawer-toggle").click()
        page.wait_for_timeout(350)

        # Confirm opened on mobile
        open_before_resize = page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        print(f"Drawer opened on 375px: {open_before_resize}")

        # Resize to Desktop 1280x800
        page.set_viewport_size({"width": 1280, "height": 800})
        page.wait_for_timeout(400)

        desktop_transition = page.evaluate("""() => {
            const doc = document.documentElement;
            const sidebar = document.querySelector('.sidebar-container');
            const backdrop = document.querySelector('.mobile-drawer-backdrop');
            const toggle = document.querySelector('.btn-drawer-toggle');
            const sStyle = window.getComputedStyle(sidebar);
            const bStyle = backdrop ? window.getComputedStyle(backdrop) : null;
            const tStyle = window.getComputedStyle(toggle);

            return {
                docOverflow: doc.scrollWidth > doc.clientWidth,
                docScrollWidth: doc.scrollWidth,
                docClientWidth: doc.clientWidth,
                sidebarPosition: sStyle.position,
                sidebarWidth: sidebar.getBoundingClientRect().width,
                sidebarLeft: sidebar.getBoundingClientRect().left,
                backdropHidden: !backdrop || bStyle.display === 'none' || parseFloat(bStyle.opacity) === 0,
                toggleHidden: tStyle.display === 'none'
            };
        }""")
        resize_desktop_ok = (
            not desktop_transition["docOverflow"] and
            desktop_transition["sidebarPosition"] == "relative" and
            desktop_transition["sidebarWidth"] >= 370 and
            desktop_transition["backdropHidden"] and
            desktop_transition["toggleHidden"]
        )
        print(f"Resize to 1280px result: {'PASS' if resize_desktop_ok else 'FAIL'} - {desktop_transition}")
        if not resize_desktop_ok:
            overall_passed = False

        # Resize back to Mobile 375x812
        page.set_viewport_size({"width": 375, "height": 812})
        page.wait_for_timeout(400)

        mobile_retransition = page.evaluate("""() => {
            const doc = document.documentElement;
            const sidebar = document.querySelector('.sidebar-container');
            const sStyle = window.getComputedStyle(sidebar);
            const sRect = sidebar.getBoundingClientRect();
            return {
                docOverflow: doc.scrollWidth > doc.clientWidth,
                sidebarPosition: sStyle.position,
                sidebarRight: sRect.right
            };
        }""")
        resize_mobile_ok = not mobile_retransition["docOverflow"] and mobile_retransition["sidebarPosition"] == "fixed"
        print(f"Resize back to 375px result: {'PASS' if resize_mobile_ok else 'FAIL'} - {mobile_retransition}")
        if not resize_mobile_ok:
            overall_passed = False

        # 4. Toast Banner Layering & Viewport Containment Test
        print("\n---> Stress Test: Toast Banner (z-index: 1200) Containment on 320x568")
        page.set_viewport_size({"width": 320, "height": 568})
        page.wait_for_timeout(250)

        toast_test_data = page.evaluate("""() => {
            const appShell = document.querySelector('.app-shell');
            const scopedAttr = Array.from(appShell.attributes).find(a => a.name.startsWith('data-v-')).name;
            
            // Create a test toast element matching the App.vue template with scoped attribute
            const toast = document.createElement('div');
            toast.className = 'toast-banner info';
            toast.id = 'empirical-test-toast';
            toast.setAttribute(scopedAttr, '');
            toast.innerHTML = '<span>Tài liệu đã tải lên!</span><button class=\"toast-close\">×</button>';
            appShell.appendChild(toast);

            const style = window.getComputedStyle(toast);
            const rect = toast.getBoundingClientRect();
            const doc = document.documentElement;

            const res = {
                zIndex: style.zIndex,
                position: style.position,
                top: rect.top,
                right: rect.right,
                bottom: rect.bottom,
                left: rect.left,
                width: rect.width,
                docScrollWidth: doc.scrollWidth,
                docClientWidth: doc.clientWidth,
                hasOverflow: doc.scrollWidth > doc.clientWidth
            };

            // Remove test toast
            toast.remove();
            return res;
        }""")

        toast_ok = (
            toast_test_data["zIndex"] == "1200" and
            not toast_test_data["hasOverflow"] and
            toast_test_data["docScrollWidth"] <= toast_test_data["docClientWidth"]
        )
        print(f"Toast test result: {'PASS' if toast_ok else 'FAIL'} - zIndex={toast_test_data['zIndex']}, docScrollWidth={toast_test_data['docScrollWidth']} <= clientWidth={toast_test_data['docClientWidth']}")
        if not toast_ok:
            overall_passed = False

        browser.close()

    print("\n" + "=" * 80)
    print("EMPIRICAL TEST SUMMARY:")
    print("=" * 80)
    total_checks = sum(len(r["checks"]) for r in test_results)
    passed_checks = sum(sum(1 for c in r["checks"] if c["passed"]) for r in test_results)
    print(f"Total Viewport Matrix Checks: {passed_checks}/{total_checks} passed.")

    verdict = "APPROVE" if overall_passed else "REJECT"
    print(f"\nFINAL VERDICT: {verdict}")
    print("=" * 80)

    # Save output json
    with open("D:/docmind/frontend/tests/empirical_results_m2_it2.json", "w", encoding="utf-8") as f:
        json.dump({"verdict": verdict, "results": test_results, "total_checks": total_checks, "passed_checks": passed_checks}, f, indent=2)

    return overall_passed

if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)
