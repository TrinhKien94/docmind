import sys
import json
import time
from playwright.sync_api import sync_playwright

CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
BASE_URL = "http://localhost:3000"

def test_modal_adversarial():
    print("=" * 70)
    print("ADVERSARIAL DEEP STRESS TEST: MODAL LAYERING & INTERACTIONS")
    print("=" * 70)

    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)
        page = browser.new_page()

        page.route("**/api/**", lambda route: route.fulfill(status=500, body='{"error":"offline_mock"}'))

        page.set_viewport_size({"width": 375, "height": 812})
        page.goto(BASE_URL, wait_until="domcontentloaded")
        page.wait_for_selector(".app-shell", timeout=5000)
        page.wait_for_selector(".doc-card", timeout=5000)

        # Step 1: Open mobile drawer
        toggle_btn = page.locator(".btn-drawer-toggle")
        toggle_btn.click()
        page.wait_for_timeout(400)
        assert page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")

        # Step 2: Open document detail modal
        detail_btn = page.locator(".documents-list .doc-card .btn-icon").first
        detail_btn.click()
        page.wait_for_timeout(400)

        # Check modal presence
        modal = page.locator(".modal-backdrop")
        assert modal.is_visible(), "Modal backdrop should be visible"

        # Check z-index stacking hierarchy
        stacking_data = page.evaluate("""() => {
            const sidebar = document.querySelector('.sidebar-container');
            const drawerBackdrop = document.querySelector('.mobile-drawer-backdrop');
            const modalBackdrop = document.querySelector('.modal-backdrop');
            const modalCard = document.querySelector('.modal-card');
            
            return {
                sidebar_zIndex: parseInt(window.getComputedStyle(sidebar).zIndex) || 0,
                drawer_backdrop_zIndex: parseInt(window.getComputedStyle(drawerBackdrop).zIndex) || 0,
                modal_backdrop_zIndex: parseInt(window.getComputedStyle(modalBackdrop).zIndex) || 0
            };
        }""")
        print(f"Computed z-indices: {stacking_data}")
        assert stacking_data["sidebar_zIndex"] == 1000, f"Expected sidebar 1000, got {stacking_data['sidebar_zIndex']}"
        assert stacking_data["modal_backdrop_zIndex"] == 1100, f"Expected modal 1100, got {stacking_data['modal_backdrop_zIndex']}"
        assert stacking_data["modal_backdrop_zIndex"] > stacking_data["sidebar_zIndex"], "Modal must be strictly higher than sidebar"
        print("[PASS] Modal backdrop z-index is strictly greater than mobile drawer (1100 > 1000)")

        # Step 3: Trigger toast while modal is open, verify toast z-index >= 1200
        page.evaluate("() => { window.dispatchEvent(new CustomEvent('test-toast')); }")
        astrbot_btn = page.locator("[data-testid='open-astrbot-btn']")
        if astrbot_btn.count() > 0:
            # Note: astrbot_btn is behind modal backdrop on mobile, which is correct!
            pass

        # Check toast style defined in stylesheet / scoped element
        toast_z = page.evaluate("""() => {
            const app = document.querySelector('.app-shell');
            const scopeAttr = Array.from(app.attributes).find(a => a.name.startsWith('data-v-'));
            const t = document.createElement('div');
            t.className = 'toast-banner info';
            if (scopeAttr) {
                t.setAttribute(scopeAttr.name, '');
            }
            app.appendChild(t);
            const z = parseInt(window.getComputedStyle(t).zIndex) || 0;
            t.remove();
            return z;
        }""")
        print(f"Computed Toast Banner z-index: {toast_z}")
        assert toast_z == 1200, f"Expected toast z-index 1200, got {toast_z}"
        assert toast_z > stacking_data["modal_backdrop_zIndex"], "Toast must be higher than modal"
        print("[PASS] Toast banner z-index is 1200 (strictly higher than modal 1100)")

        # Step 4: Test Modal Close Button (×)
        close_btn = page.locator(".modal-header .btn-close")
        close_btn.click()
        page.wait_for_timeout(300)
        assert not modal.is_visible(), "Modal should close after clicking ×"
        drawer_still_open = page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        assert drawer_still_open, "Drawer should remain open after closing modal"
        print("[PASS] Modal closes cleanly via close button × while drawer remains open")

        # Step 5: Re-open modal, test closing via backdrop click
        detail_btn.click()
        page.wait_for_timeout(400)
        assert modal.is_visible()

        # Click top-left margin (10, 10) which is on .modal-backdrop outside .modal-card
        page.click(".modal-backdrop", position={"x": 10, "y": 10})
        page.wait_for_timeout(300)
        assert not modal.is_visible(), "Modal should close after clicking backdrop margin"
        drawer_still_open = page.evaluate("() => document.querySelector('.sidebar-container').classList.contains('is-mobile-open')")
        assert drawer_still_open, "Drawer should remain open after closing modal via backdrop"
        print("[PASS] Modal closes cleanly via backdrop click while drawer remains open")

        # Step 6: Re-open modal, test closing via footer 'Đóng' button
        detail_btn.click()
        page.wait_for_timeout(400)
        assert modal.is_visible()

        footer_btn = page.locator(".modal-footer .btn-secondary")
        footer_btn.click()
        page.wait_for_timeout(300)
        assert not modal.is_visible(), "Modal should close after clicking footer Đóng button"
        print("[PASS] Modal closes cleanly via footer Close button")

        # Step 7: Re-open modal, test elementFromPoint across 5 probe points inside modal card
        detail_btn.click()
        page.wait_for_timeout(400)
        assert modal.is_visible()

        probe_results = page.evaluate("""() => {
            const card = document.querySelector('.modal-card');
            const rect = card.getBoundingClientRect();
            const points = [
                { name: 'center', x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 },
                { name: 'top-half', x: rect.left + rect.width / 2, y: rect.top + rect.height * 0.25 },
                { name: 'bottom-half', x: rect.left + rect.width / 2, y: rect.top + rect.height * 0.75 },
                { name: 'left-quarter', x: rect.left + rect.width * 0.25, y: rect.top + rect.height / 2 },
                { name: 'right-quarter', x: rect.left + rect.width * 0.75, y: rect.top + rect.height / 2 }
            ];

            return points.map(pt => {
                const el = document.elementFromPoint(pt.x, pt.y);
                return {
                    name: pt.name,
                    x: pt.x,
                    y: pt.y,
                    tag: el ? el.tagName : null,
                    class: el ? el.className : null,
                    inside_modal_card: el ? !!el.closest('.modal-card') : false,
                    inside_sidebar: el ? !!el.closest('.sidebar-container') : false
                };
            });
        }""")
        print("Probe results across modal card:")
        for pr in probe_results:
            print(f"  Point {pr['name']}: tag={pr['tag']}, class={pr['class']}, inside_card={pr['inside_modal_card']}, inside_sidebar={pr['inside_sidebar']}")
            assert pr["inside_modal_card"] and not pr["inside_sidebar"], f"Occlusion detected at {pr['name']}!"
        print("[PASS] All 5 multi-point coordinate probes hit inside modal-card with 0 occlusion from sidebar")

        browser.close()

    print("=" * 70)
    print("ALL ADVERSARIAL MODAL STRESS TESTS PASSED SUCCESSFULLY!")
    print("=" * 70)
    return True

if __name__ == "__main__":
    success = test_modal_adversarial()
    sys.exit(0 if success else 1)
