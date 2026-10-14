#!/usr/bin/env python3
"""
Test conversation interaction, bubble wrapping, and scroll containment at 320x568.
"""
from playwright.sync_api import sync_playwright

CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
BASE_URL = "http://localhost:3000"

def test_conversation():
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME_PATH, headless=True)
        page = browser.new_page()
        page.set_viewport_size({"width": 320, "height": 568})
        page.goto(BASE_URL, wait_until="domcontentloaded")
        page.wait_for_selector(".starter-card", timeout=5000)

        # Click the first starter card to initiate a question
        page.click(".starter-card")
        page.wait_for_timeout(2000) # wait for mock or live response

        res = page.evaluate("""() => {
            const doc = document.documentElement;
            const msgs = document.querySelector('.messages-area');
            const header = document.querySelector('.chat-header');
            const input = document.querySelector('.chat-input-wrapper');
            const messageBubbles = Array.from(document.querySelectorAll('.message-bubble'));
            const citationCards = Array.from(document.querySelectorAll('.citation-card'));

            return {
                docOverflow: doc.scrollWidth > doc.clientWidth,
                docScrollWidth: doc.scrollWidth,
                docClientWidth: doc.clientWidth,
                headerOverflow: header.scrollWidth > header.clientWidth,
                headerScrollWidth: header.scrollWidth,
                headerClientWidth: header.clientWidth,
                msgsScrollHeight: msgs.scrollHeight,
                msgsClientHeight: msgs.clientHeight,
                inputBottom: input.getBoundingClientRect().bottom,
                bubbleWidths: messageBubbles.map(b => b.getBoundingClientRect().width),
                bubbleOverflows: messageBubbles.map(b => b.scrollWidth > b.clientWidth),
                citationOverflows: citationCards.map(c => c.scrollWidth > c.clientWidth)
            };
        }""")

        print("Conversation Test at 320x568 Results:")
        print(f"  docOverflow: {res['docOverflow']} (scrollWidth: {res['docScrollWidth']}, clientWidth: {res['docClientWidth']})")
        print(f"  headerOverflow: {res['headerOverflow']} (scrollWidth: {res['headerScrollWidth']}, clientWidth: {res['headerClientWidth']})")
        print(f"  inputBottom: {res['inputBottom']} (viewport height: 568)")
        print(f"  msgs: scrollHeight={res['msgsScrollHeight']}, clientHeight={res['msgsClientHeight']}")
        print(f"  bubbleOverflows: {res['bubbleOverflows']}")
        print(f"  citationOverflows: {res['citationOverflows']}")

        assert not res['docOverflow'], "Document must not overflow during active conversation!"
        assert not res['headerOverflow'], "Header must not overflow during active conversation!"
        assert res['inputBottom'] <= 568 + 1, "Input bar must remain anchored above bottom edge!"
        assert not any(res['bubbleOverflows']), "Message bubbles must not overflow!"
        assert not any(res['citationOverflows']), "Citation cards must not overflow!"

        print("\n>>> CONVERSATION SCROLL & OVERFLOW TEST PASSED! <<<")
        browser.close()

if __name__ == "__main__":
    test_conversation()
