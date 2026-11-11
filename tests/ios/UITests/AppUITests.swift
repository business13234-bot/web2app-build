import XCTest

/// Drives the generated app on the simulator against tests/site/server.mjs and checks every feature.
/// Each check prints "W2A-CHECK PASS|FAIL <name> <detail>"; the test fails at the end if any check failed.
final class AppUITests: XCTestCase {
    private let app = XCUIApplication()
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    private var failures: [String] = []
    private let site = ProcessInfo.processInfo.environment["W2A_SITE"] ?? "http://127.0.0.1:8090"

    override func setUp() {
        continueAfterFailure = true
    }

    // MARK: helpers

    private func check(_ name: String, _ ok: Bool, _ detail: String = "") {
        print("W2A-CHECK \(ok ? "PASS" : "FAIL") \(name) \(detail)")
        if !ok { failures.append(name) }
    }

    private func shot(_ name: String) {
        let a = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }

    private func web(_ text: String, _ timeout: TimeInterval = 8) -> Bool {
        return app.webViews.staticTexts[text].waitForExistence(timeout: timeout)
    }

    /// The test page writes what the app changed into a line starting with "js=".
    private func status(_ timeout: TimeInterval = 8) -> String {
        let q = app.webViews.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'js='")).firstMatch
        return q.waitForExistence(timeout: timeout) ? q.label : ""
    }

    private func value(_ status: String, _ key: String) -> String {
        for part in status.split(separator: " ") where part.hasPrefix(key + "=") {
            return String(part.dropFirst(key.count + 1))
        }
        return ""
    }

    /// Accepts system permission alerts; returns how many were shown.
    @discardableResult
    private func allowAlerts(_ seconds: TimeInterval = 6) -> [String] {
        var seen: [String] = []
        var end = Date().addingTimeInterval(seconds)
        while Date() < end {
            let alert = springboard.alerts.firstMatch
            if alert.waitForExistence(timeout: 1) {
                // Permission questions come one after another: keep watching a few seconds after each.
                end = max(end, Date().addingTimeInterval(4))
                // The alert can close on its own between these steps; only touch it while it is there.
                guard alert.exists else { continue }
                seen.append(alert.label)
                for title in ["Allow", "Allow While Using App", "Allow Once", "OK", "Open", "Not Now"] {
                    let b = alert.buttons[title]
                    if b.exists, alert.exists { softly { b.tap() }; break }
                }
            }
        }
        return seen
    }

    /// The app lock: the workflow shows a matching simulated face every 2 s. Waits for the lock screen,
    /// accepts the one-time Face ID question, and waits until the lock is gone. Returns true when it opened.
    @discardableResult
    private func passLock(_ seconds: TimeInterval = 40) -> Bool {
        let lock = app.buttons["Unlock"]
        // The lock comes after the splash, which can take a while on a cold simulator.
        let splashGone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.otherElements["Splash"].firstMatch)
        _ = XCTWaiter().wait(for: [splashGone], timeout: 15)
        if !lock.waitForExistence(timeout: 6) { return true }
        let start = Date()
        let end = start.addingTimeInterval(seconds)
        var tapped = 0
        while Date() < end {
            for alert in [springboard.alerts.firstMatch, app.alerts.firstMatch] where alert.exists {
                for title in ["OK", "Allow", "Continue"] where alert.buttons[title].exists {
                    softly { alert.buttons[title].tap() }
                    break
                }
            }
            if !lock.exists || !lock.isHittable { return true }
            // Press Unlock only once a try has ended (the app shows its error code), or after a long
            // quiet spell: a press while the Face ID sheet is up cancels it (error -2).
            let value = (lock.value as? String) ?? ""
            let waited = Date().timeIntervalSince(start)
            let due = !value.isEmpty ? waited > Double(3 + tapped * 4) : waited > Double(10 + tapped * 8)
            if due, tapped < 4 {
                usleep(300_000)
                guard lock.exists, lock.isHittable else { return true }
                print("W2A-INFO lock still shown, value=\(value) tapping Unlock")
                softly { lock.tap() }
                tapped += 1
            }
            usleep(400_000)
        }
        print("W2A-INFO lock never opened, value=\(lock.value ?? "")")
        return false
    }

    /// Taps on things that can vanish by themselves (system alerts, the lock) must not fail the run.
    private func softly(_ body: () -> Void) {
        let o = XCTExpectedFailure.Options(); o.isStrict = false
        XCTExpectFailure("element may have gone away", options: o, failingBlock: body)
    }

    private func server(_ path: String) {
        let done = expectation(description: path)
        URLSession.shared.dataTask(with: URL(string: site + path)!) { _, _, _ in done.fulfill() }.resume()
        // A cold simulator can be slow on its first request; a late answer is not a test failure.
        if XCTWaiter().wait(for: [done], timeout: 30) != .completed { print("W2A-INFO server \(path) slow") }
    }

    private func relaunch() {
        app.terminate()
        app.launch()
    }

    private func finish() {
        print("W2A-SUMMARY failures=\(failures.count) \(failures.joined(separator: ","))")
        XCTAssertTrue(failures.isEmpty, "Failed checks: \(failures.joined(separator: ", "))")
    }

    // MARK: variant a — splash with logo and name, welcome slides, ads off on iOS, remember page, swipe

    func testVariantA() {
        server("/set-version?v=1")
        app.launchArguments = ["-w2aSelfTest"]
        app.launch()
        check("splash shows app name", app.staticTexts["W2A Test"].waitForExistence(timeout: 4))
        shot("a01-splash")
        check("app lock opens with Face ID", passLock())

        let welcome = app.staticTexts["Welcome"].waitForExistence(timeout: 12)
        check("welcome slides", welcome)
        check("no permission alert over slides", !springboard.alerts.firstMatch.exists)
        shot("a02-welcome")
        if welcome {
            app.buttons["Next"].tap()
            check("slide two", app.staticTexts["Fast"].waitForExistence(timeout: 4))
            app.buttons["Get started"].tap()
        }
        let alerts = allowAlerts(8)
        check("permissions asked after slides", !alerts.isEmpty, alerts.joined(separator: " | "))

        check("home page loads", web("Home", 12))
        let s = status()
        shot("a03-home")
        check("custom JavaScript runs", value(s, "js") == "ran", s)
        check("custom CSS applied", value(s, "css") == "applied", s)
        check("native feel (no text select)", value(s, "select") == "none", s)
        check("JavaScript bridge", value(s, "bridge") == "ios", s)
        check("content blocker", value(s, "tracker") == "blocked", s)
        let textHeight = Int(value(s, "text")) ?? 0
        check("text size 150%", textHeight >= 22, "probe height \(textHeight)")
        check("mobile layout", (Int(value(s, "width")) ?? 0) < 600, s)

        check("top bar title", app.staticTexts.matching(NSPredicate(format: "label == %@", "Home")).count >= 2)
        check("refresh button", app.buttons["Refresh"].exists)
        check("floating button", app.buttons["Chat on WhatsApp"].exists)
        for tab in ["Home", "Search", "Cart", "Me"] {
            check("tab \(tab)", app.buttons[tab].exists)
        }

        app.buttons["Cart"].tap()
        check("tab opens page", web("Cart page"))
        check("back button appears", app.buttons["Back"].waitForExistence(timeout: 4))
        shot("a04-cart")
        app.buttons["Back"].tap()
        check("back goes back", web("Home"))

        // The page may still be reloading after going back: a tap then is lost, so try twice.
        var toast = false
        for _ in 0..<2 where !toast {
            sleep(1)
            app.webViews.buttons["Show toast"].tap()
            toast = app.staticTexts["Bridge toast works"].waitForExistence(timeout: 4)
        }
        check("bridge toast", toast)
        shot("a05-toast")

        app.webViews.links["Deep link inside page"].tap()
        check("deep link inside page", web("Cart page"))

        if #available(iOS 16.4, *) {
            server("/sim?do=open&url=" + "w2atest%3A%2F%2Fsearch")
            allowAlerts(3)
            check("deep link from outside", web("Search page", 10))
        }
        shot("a06-deeplink")

        // Remember last page + rating on the 2nd launch (Apple's own window).
        relaunch()
        passLock()
        check("remembers last page", web("Search page", 12))
        // iOS decides itself whether Apple's rating window really shows, so the check is that the app asked.
        let shown = springboard.alerts.firstMatch.waitForExistence(timeout: 6) || app.alerts.firstMatch.exists
        shot("a07-second-launch")
        check("rating asked on 2nd launch", waitFor("rateAsks", "1", 8), "window shown: \(shown)")
        allowAlerts(2)

        // Update prompt.
        server("/set-version?v=5")
        relaunch()
        passLock()
        let update = app.alerts["Update available"].waitForExistence(timeout: 12)
        shot("a08-update")
        check("update prompt", update)
        if update { app.alerts["Update available"].buttons["Later"].tap() }
        server("/set-version?v=1")
        sleep(3)
        finish()
    }

    // MARK: variant b — spinner, page title, home/share buttons, desktop mode, FAB link, name-only splash

    func testVariantB() {
        server("/set-version?v=1")
        app.launchArguments = ["-w2aSelfTest"]
        app.launch()
        check("name-only splash", app.staticTexts["W2A Test"].waitForExistence(timeout: 4))
        passLock()
        allowAlerts(6)
        check("home page loads", web("Home", 12))
        let s = status()
        shot("b01-home")
        check("desktop layout", (Int(value(s, "width")) ?? 0) >= 900, s)
        check("page title in top bar", app.staticTexts["Home"].exists)
        check("home button", app.buttons["Home"].exists)
        check("share button", app.buttons["Share"].exists)

        app.buttons["Open link"].tap()
        check("floating button opens link", web("Page two"))
        shot("b02-fab")
        app.buttons["Share"].tap()
        let sheet = app.otherElements["ActivityListView"].waitForExistence(timeout: 5) || app.sheets.firstMatch.exists
            || app.collectionViews.firstMatch.waitForExistence(timeout: 2)
        shot("b03-share")
        check("share sheet", sheet)
        if app.buttons["Close"].exists { app.buttons["Close"].tap() } else { app.swipeDown() }

        app.buttons["Home"].firstMatch.tap()
        check("home button works", web("Home"))
        finish()
    }

    // MARK: variant c — the 20 features added in 1.2

    /// What the app reports about itself in test runs (a hidden label holding JSON).
    private func selfTest() -> [String: Any] {
        let e = app.staticTexts.matching(identifier: "w2a-selftest").firstMatch
        guard e.waitForExistence(timeout: 3), let data = e.label.data(using: .utf8),
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return [:] }
        return json
    }

    private func st(_ key: String) -> String {
        return selfTest()[key].map { "\($0)" } ?? ""
    }

    /// Waits until the app reports a value.
    private func waitFor(_ key: String, _ expected: String, _ seconds: TimeInterval = 6) -> Bool {
        let end = Date().addingTimeInterval(seconds)
        repeat {
            if st(key) == expected { return true }
            usleep(400_000)
        } while Date() < end
        return false
    }

    private func home() {
        app.buttons["Home"].firstMatch.tap()
        _ = web("Probe text", 8)
    }

    private func openFromHome(_ link: String, expect: String) -> Bool {
        home()
        let l = app.webViews.links[link]
        guard l.waitForExistence(timeout: 5) else { return false }
        // Right after a menu closes the page can still be settling; wait, then tap by position.
        usleep(1_000_000)
        l.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        return web(expect, 8)
    }

    private func snack(_ text: String, _ seconds: TimeInterval = 5) -> Bool {
        return app.staticTexts[text].waitForExistence(timeout: seconds)
    }

    /// Closes the in-app Safari window; a tap while it is still sliding in is ignored, so it tries again.
    private func closeBrowserWindow() {
        let close = app.buttons["Close"].firstMatch
        for _ in 0..<4 where close.exists {
            sleep(1)
            // Safari runs in its own process, so its button can report "not hittable": tap where it is.
            close.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: close)
            if XCTWaiter().wait(for: [gone], timeout: 3) == .completed { return }
        }
    }

    // MARK: variant d — features added in 1.4 (start page: /feat)

    private func label(beginning prefix: String, _ timeout: TimeInterval = 8) -> String {
        let q = app.webViews.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
        return q.waitForExistence(timeout: timeout) ? q.label : ""
    }

    private func tapWebLink(_ text: String) -> Bool {
        _ = allowAlerts(1)
        let l = app.webViews.links[text].firstMatch
        guard l.waitForExistence(timeout: 6) else { return false }
        l.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        return true
    }

    func testVariantD() {
        app.launch()
        shot("d-splash")
        allowAlerts(4)
        let lockShown = app.otherElements["LockScreen"].waitForExistence(timeout: 15)
        shot("d-lock")
        check("themed lock screen", lockShown && app.staticTexts["Locked for your privacy"].exists)
        check("lock opens", passLock())
        // Permission questions (camera etc.) come after the lock and would swallow the taps below.
        _ = allowAlerts(6)
        check("feature page loads", web("Feature page", 15))
        let fs = label(beginning: "font=", 10)
        check("app font", value(fs, "font") == "Poppins", fs)
        check("progress bar", value(fs, "bar") == "true", fs)
        check("speech bridge", value(fs, "speech") == "object", fs)
        shot("d-feature-page")

        // Read aloud: the page shows speaking=true while the voice talks.
        _ = allowAlerts(1)
        let speak = app.webViews.buttons["Speak"].firstMatch
        if speak.waitForExistence(timeout: 5) { speak.tap() }
        let talking = app.webViews.staticTexts["speaking=true"].waitForExistence(timeout: 8)
        check("speech speaks", talking)

        // Stay in app: an outside link is not opened.
        check("outside link tapped", tapWebLink("External site"))
        check("stay in app message", snack("Stay-in-app test message", 6))
        check("still on page", web("Feature page", 3))

        // Friendly error page for a server error and a missing page.
        check("server error link", tapWebLink("Server error"))
        check("error page on 500", app.staticTexts["Oops test"].waitForExistence(timeout: 10))
        shot("d-error-page")
        app.buttons["Try again"].firstMatch.tap()
        check("error page stays on retry", app.staticTexts["Oops test"].waitForExistence(timeout: 8))
        app.buttons["Home"].firstMatch.tap()
        check("home leaves error page", web("Feature page", 10) && !app.staticTexts["Oops test"].isHittable)
        check("missing page link", tapWebLink("Missing page"))
        check("error page on 404", app.staticTexts["Oops test"].waitForExistence(timeout: 10))
        app.buttons["Home"].firstMatch.tap()
        _ = web("Feature page", 10)

        // Picture viewer: tap a big picture, it opens full screen with a close button.
        let pic = app.webViews.images["big photo"].firstMatch
        if pic.waitForExistence(timeout: 6) {
            pic.swipeUp()
            pic.tap()
        }
        let close = app.webViews.buttons["Close"].firstMatch
        check("picture viewer opens", close.waitForExistence(timeout: 6))
        shot("d-viewer")
        pic.doubleTap()
        shot("d-viewer-zoom")
        if close.exists { close.tap() }
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: close)
        check("picture viewer closes", XCTWaiter().wait(for: [gone], timeout: 4) == .completed)

        // Fresh page after a long break (1 minute): the page load time changes.
        let before = value(label(beginning: "font=", 8), "loaded")
        XCUIDevice.shared.press(.home)
        sleep(66)
        app.activate()
        passLock()
        sleep(3)
        let after = value(label(beginning: "font=", 10), "loaded")
        check("reload after a long break", !before.isEmpty && !after.isEmpty && before != after, "\(before) -> \(after)")
        finish()
    }

    // MARK: variant e — an app made from a ZIP: pages, CSS, JS, fetch and pictures come from inside the app

    func testVariantE() {
        app.launch()
        allowAlerts(3)
        check("zip first page", web("Offline ZIP site", 20))
        let js = label(beginning: "zip js", 10)
        check("zip script and fetch", js.contains("w2a-app:") && js.contains("json loaded"), js)
        check("zip picture", app.webViews.images["logo"].waitForExistence(timeout: 5))
        shot("e-home")
        check("zip link", tapWebLink("About page"))
        check("zip second page", web("About (from ZIP)", 8))
        shot("e-about")
        check("zip back link", tapWebLink("Back home"))
        check("zip home again", web("Offline ZIP site", 8))
        check("zip folder link", tapWebLink("Docs folder"))
        check("zip folder index", web("Docs index", 8))
        relaunch()
        check("zip after relaunch", web("Offline ZIP site", 20))
        finish()
    }

    func testVariantC() {
        server("/set-version?v=1")
        server("/set-down?on=0")
        server("/set-notice?id=n1&msg=Live%20notice%20test")
        app.launchArguments = ["-w2aSelfTest"]
        app.launch()
        check("app lock opens", passLock())

        // 1. Age check: "No" blocks, the next start asks again, "Yes" is remembered.
        let ageQ = app.staticTexts["Are you 18 or older?"].waitForExistence(timeout: 10)
        check("age check shown", ageQ)
        shot("c01-age")
        if ageQ {
            app.buttons["No"].tap()
            check("age check blocks", app.staticTexts["Age blocked test"].waitForExistence(timeout: 4))
            shot("c02-age-blocked")
            relaunch()
            passLock()
            check("age check asked again", app.staticTexts["Are you 18 or older?"].waitForExistence(timeout: 10))
            app.buttons["Yes, I am 18+"].tap()
        }

        // 2. Privacy consent, with the policy opened on top of it.
        let consent = app.staticTexts["Consent test message"].waitForExistence(timeout: 6)
        check("consent shown", consent)
        shot("c03-consent")
        if consent {
            app.buttons["Read the privacy policy"].tap()
            let close = app.buttons["Close"].firstMatch
            check("policy opens in app", close.waitForExistence(timeout: 8))
            shot("c04-policy")
            closeBrowserWindow()
            check("consent still waits", app.buttons["Accept"].waitForExistence(timeout: 5))
            app.buttons["Accept"].tap()
        }
        allowAlerts(8)

        // 3. Announcement once, with its link.
        let news = app.alerts["Test news"]
        check("announcement", news.waitForExistence(timeout: 10))
        shot("c05-announcement")
        if news.exists {
            check("announcement text", news.staticTexts["Announcement body"].exists)
            news.buttons["Open page"].tap()
            check("announcement link", web("Page two", 8))
        }

        // 4. Notice bar from the website.
        // The notice text carries the link trait, so it is found by label, whatever its element type.
        check("notice bar", app.descendants(matching: .any)["Live notice test  ›"].firstMatch.waitForExistence(timeout: 6)
              && st("notice") == "1")
        shot("c06-notice")

        // 5. Side menu with the open page lit up.
        home()
        app.buttons["Menu"].firstMatch.tap()
        let homeRow = app.buttons["drawer.Home"]
        check("side menu opens", homeRow.waitForExistence(timeout: 4))
        check("side menu marks home", homeRow.isSelected && !app.buttons["drawer.Cart"].isSelected)
        shot("c07-drawer")
        if app.buttons["drawer.Ticket"].exists { app.buttons["drawer.Ticket"].tap() }
        check("side menu opens page", web("Show this at the door", 8))

        // 6. Page colour and brightness on the ticket page.
        check("page colour on bars", waitFor("pageTheme", "#1B5E20"), st("topBar"))
        check("top bar takes page colour", st("topBar") == "#1B5E20", st("topBar"))
        check("bright screen on ticket", waitFor("bright", "1"), "\(selfTest())")
        shot("c08-ticket")
        app.buttons["Menu"].firstMatch.tap()
        check("side menu marks ticket", app.buttons["drawer.Ticket"].waitForExistence(timeout: 4)
              && app.buttons["drawer.Ticket"].isSelected && !app.buttons["drawer.Home"].isSelected)
        app.buttons["Close menu"].firstMatch.tap()
        check("side menu closes", waitFor("drawer", "0"))

        check("themed page colour", openFromHome("Themed page", expect: "Pink theme colour") && waitFor("pageTheme", "#C2185B"))
        home()
        check("colour back to normal", waitFor("pageTheme", "") && waitFor("bright", "0"), "\(selfTest())")

        // 7. Find in page and print, from the More menu.
        app.buttons["More"].firstMatch.tap()
        let findItem = app.buttons["Find in page"].firstMatch
        check("more menu", findItem.waitForExistence(timeout: 4) && app.buttons["Print"].exists && app.buttons["Share"].exists)
        shot("c09-more")
        if findItem.exists {
            findItem.tap()
            let field = app.searchFields.firstMatch.exists ? app.searchFields.firstMatch : app.textFields.firstMatch
            if field.waitForExistence(timeout: 4) {
                field.typeText("needle")
                sleep(1)
                let count = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] '3'")).firstMatch
                check("find in page counts", count.waitForExistence(timeout: 4), count.exists ? count.label : "")
                shot("c10-find")
                app.buttons["Done"].firstMatch.exists ? app.buttons["Done"].firstMatch.tap() : app.typeText("\n")
            } else {
                check("find in page counts", false, "no search field")
            }
        }
        app.buttons["More"].firstMatch.tap()
        if app.buttons["Print"].firstMatch.waitForExistence(timeout: 4) {
            app.buttons["Print"].firstMatch.tap()
            let print = app.navigationBars.firstMatch.waitForExistence(timeout: 8) || app.buttons["Cancel"].waitForExistence(timeout: 2)
            shot("c11-print")
            check("print window", print)
            for name in ["Cancel", "Close"] where app.buttons[name].firstMatch.exists {
                app.buttons[name].firstMatch.tap()
                break
            }
            sleep(1)
        }

        // 8. Back-to-top button on a long page.
        if openFromHome("Long page", expect: "Scroll down") {
            for _ in 0..<4 { app.webViews.firstMatch.swipeUp() }
            let top = app.buttons["Back to top"]
            check("back-to-top shows", top.waitForExistence(timeout: 4) && top.isHittable)
            shot("c12-totop")
            if top.exists { top.tap() }
            check("back-to-top scrolls up", waitFor("toTop", "0"))
        } else {
            check("long page", false)
        }

        // 9. Long-press menu: image and link.
        if openFromHome("Image page", expect: "Image page") {
            let pic = app.webViews.images.firstMatch
            if pic.waitForExistence(timeout: 4) { pic.press(forDuration: 1.2) }
            let save = app.buttons["Save image"]
            check("image menu", save.waitForExistence(timeout: 4) && app.buttons["Open link"].exists)
            shot("c13-longpress")
            if save.exists {
                save.tap()
                allowAlerts(4)
                check("image saved", snack("Saved to Photos", 8))
            }
        }
        home()
        let cartLink = app.webViews.links["Cart"]
        if cartLink.waitForExistence(timeout: 4) { cartLink.press(forDuration: 1.2) }
        let copy = app.buttons["Copy link"]
        check("link menu", copy.waitForExistence(timeout: 4) && !app.buttons["Save image"].exists)
        if copy.exists {
            copy.tap()
            check("link copied", snack("Link copied"))
        }

        // 10. Other websites open inside the app.
        home()
        app.webViews.links["External site"].tap()
        let closeBrowser = app.buttons["Close"].firstMatch
        check("in-app browser", closeBrowser.waitForExistence(timeout: 10))
        shot("c14-inapp")
        closeBrowserWindow()
        check("in-app browser closes", web("Probe text", 6))

        // 11. Local notification scheduled by the page; tapping it opens its page.
        if openFromHome("Notify page", expect: "Notify page") {
            app.webViews.buttons["Schedule in 5 s"].tap()
            allowAlerts(3)
            let id = app.webViews.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'id='")).firstMatch
            _ = id.waitForExistence(timeout: 4)
            let idText = id.exists ? id.label : ""
            check("notification scheduled", idText.count > 3 && idText != "id=-1" && idText != "id=none" && idText != "id=?", idText)
            let banner = springboard.otherElements.matching(NSPredicate(format: "label CONTAINS 'Local test'")).firstMatch
            let shown = banner.waitForExistence(timeout: 15)
            shot("c15-notification")
            check("notification shows", shown)
            if shown {
                banner.tap()
                check("notification opens page", web("Cart page", 8))
            }
        }

        // 12. Shake to reload (the test website shakes the simulator).
        server("/sim?do=shake")
        check("shake reloads", snack("Reloading…", 5))

        // 13. Hidden in the app switcher; music keeps playing in the background.
        check("audio keeps playing setting", st("audio") == "AVAudioSessionCategoryPlayback", st("audio"))
        if openFromHome("Audio page", expect: "Audio page") {
            app.webViews.buttons["Play"].tap()
            sleep(2)
            XCUIDevice.shared.press(.home)
            sleep(7)
            app.activate()
            passLock(6)
            let title = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 't='")).firstMatch
            _ = title.waitForExistence(timeout: 4)
            let label = title.exists ? title.label : ""
            let played = Double(label.split(separator: " ").first?.dropFirst(2) ?? "") ?? 0
            check("audio played in background", played >= 7 && label.contains("p=false"), label)
            check("privacy cover used", (Int(st("covers")) ?? 0) >= 1, st("covers"))
            check("privacy cover removed", !app.otherElements["PrivacyCover"].exists)
            app.webViews.firstMatch.tap()
        }

        // 14. Offline: saved copies open, unknown pages show the offline screen.
        _ = openFromHome("Search", expect: "Search page")
        server("/set-down?on=1")
        app.buttons["Refresh"].firstMatch.tap()
        check("saved copy when site is down", snack("Offline copy test", 10) && web("Search page", 4))
        shot("c16-offline")
        app.buttons["Me"].firstMatch.tap()
        check("offline screen for new page", app.staticTexts["No internet connection"].waitForExistence(timeout: 12))
        server("/set-down?on=0")
        app.buttons["Retry"].tap()
        check("retry after offline", web("Me page", 10))

        // 15. Idle reset after 1 minute without a touch.
        app.buttons["Cart"].firstMatch.tap()
        _ = web("Cart page", 6)
        sleep(80)
        check("idle reset goes home", web("Probe text", 20))
        shot("c17-idle")

        // 16. Next start: no age check, consent or announcement; the closed notice stays closed.
        if app.buttons["Close notice"].exists { app.buttons["Close notice"].tap() }
        sleep(1)
        relaunch()
        passLock()
        allowAlerts(3)
        check("home after restart", web("Probe text", 12))
        check("no start screens again", !app.staticTexts["Are you 18 or older?"].exists && !app.buttons["Accept"].exists)
        check("no announcement again", !app.alerts["Test news"].waitForExistence(timeout: 4))
        check("closed notice stays closed", !app.staticTexts["Live notice test  ›"].waitForExistence(timeout: 4))
        server("/set-notice?id=n2&msg=Second%20notice")
        relaunch()
        passLock()
        check("new notice shows", app.staticTexts["Second notice  ›"].waitForExistence(timeout: 12))
        shot("c18-restart")
        server("/set-notice?id=n1&msg=Live%20notice%20test")
        finish()
    }

    // MARK: dark mode (the workflow switches the simulator to dark first)

    func testDarkMode() {
        app.launchArguments = ["-w2aSelfTest"]
        app.launch()
        passLock()
        allowAlerts(3)
        _ = web("Home", 12)
        app.buttons["Search"].tap()
        let s = status()
        shot("d01-dark")
        check("page sees dark mode", value(s, "dark") == "true", s)
        finish()
    }
}
