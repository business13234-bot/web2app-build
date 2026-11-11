import UIKit
import UIKit.UIGestureRecognizerSubclass
import WebKit
import SafariServices
import Photos
import UserNotifications
import AVFoundation

/// The features added in Web2App 1.2: start screens, side menu, notice bar, page colours, local notifications,
/// in-app browser, long-press menu, offline copies and more. Each one does nothing unless it is switched on.
extension WebViewController {

    // MARK: - Start

    func startFeatures() {
        WebViewController.current = self
        NotificationCenter.default.addObserver(self, selector: #selector(featuresWillResignActive),
                                               name: UIApplication.willResignActiveNotification, object: nil)
        if cfg.feature("backgroundAudio") != nil {
            // Only the kind of audio is set here, so opening the app does not stop music from other apps.
            try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [])
        }
        if cfg.feature("shake") != nil {
            // A shake does the app's own action, not "Undo typing".
            UIApplication.shared.applicationSupportsShakeToEdit = false
        }
        if cfg.feature("idleReset") != nil {
            view.addGestureRecognizer(TouchWatcher { [weak self] in self?.lastTouch = Date() })
            idleTimer?.invalidate()
            idleTimer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in self?.idleCheck() }
        }
        if cfg.feature("longPress") != nil {
            let press = UILongPressGestureRecognizer(target: self, action: #selector(longPressed(_:)))
            press.minimumPressDuration = 0.5
            press.delegate = self
            webView.addGestureRecognizer(press)
        }
        if cfg.feature("scrollTop") != nil {
            observations.append(webView.scrollView.observe(\.contentOffset, options: [.new]) { [weak self] scroll, _ in
                self?.scrolled(scroll.contentOffset.y + scroll.adjustedContentInset.top)
            })
        }
        if cfg.feature("autoTheme") != nil {
            statusDefault = statusColor
            observations.append(webView.observe(\.themeColor, options: [.new]) { [weak self] web, _ in
                self?.setPageTheme(web.themeColor)
            })
        }
        if cfg.feature("pageFade") != nil {
            // What shows through while a new page fades in: the page colour, not the bar colour.
            let backdrop = UIView()
            backdrop.backgroundColor = .systemBackground
            backdrop.translatesAutoresizingMaskIntoConstraints = false
            view.insertSubview(backdrop, belowSubview: webView)
            NSLayoutConstraint.activate([
                backdrop.leadingAnchor.constraint(equalTo: webView.leadingAnchor),
                backdrop.trailingAnchor.constraint(equalTo: webView.trailingAnchor),
                backdrop.topAnchor.constraint(equalTo: webView.topAnchor),
                backdrop.bottomAnchor.constraint(equalTo: webView.bottomAnchor),
            ])
        }
        if let notice = cfg.feature("noticeBar") { loadNotice(notice) }
        if ProcessInfo.processInfo.arguments.contains("-w2aSelfTest") {
            Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in self?.updateSelfTestLabel() }
        }
    }

    /// Permission questions, Face ID and similar system windows also make the app inactive for a moment;
    /// the privacy cover is not put up for those.
    var systemPromptExpected: Bool {
        get { return Date() < promptUntil }
        set { promptUntil = newValue ? Date().addingTimeInterval(20) : .distantPast }
    }

    @objc func featuresWillResignActive() {
        if brightOn { applyBrightness(false) }
        if cfg.feature("privacyCover") != nil, !systemPromptExpected { showPrivacyCover() }
    }

    func featuresBecameActive() {
        if brightOn { applyBrightness(true) }
        hidePrivacyCover()
        lastTouch = Date()
    }

    func featuresEnteredBackground() {
        if cfg.feature("privacyCover") != nil { showPrivacyCover() }
    }

    // MARK: - Small helpers

    /// A light tap feel on the app's own buttons (haptic feedback feature).
    func tick(force: Bool = false) {
        guard force || cfg.feature("haptics") != nil else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    @objc func tickTap() { tick() }

    @objc func noAction() {}

    /// The view controller on top, to show a new window over whatever is open.
    var topController: UIViewController {
        var top: UIViewController = self
        while let next = top.presentedViewController, !next.isBeingDismissed { top = next }
        return top
    }

    /// A page of the website opens in the app; any other link outside it.
    func openLink(_ url: URL) {
        let scheme = (url.scheme ?? "").lowercased()
        if (scheme == "http" || scheme == "https") && isInternal(url) {
            webView.load(request(for: url))
        } else {
            openOutside(url)
        }
    }

    /// Other websites: inside the app (in-app browser feature) or in Safari.
    func openOutside(_ url: URL) {
        let scheme = (url.scheme ?? "").lowercased()
        guard let options = cfg.feature("inAppBrowser"), scheme == "http" || scheme == "https" else {
            UIApplication.shared.open(url)
            return
        }
        let safari = SFSafariViewController(url: url)
        safari.dismissButtonStyle = .close
        if let tint = options.color("color") ?? (theme.isLight ? nil : theme) { safari.preferredControlTintColor = tint }
        tick()
        topController.present(safari, animated: true)
    }

    /// True when the link points at the page now open. A link to the home page only matches the home page.
    func linkMatches(_ url: URL?, _ target: String) -> Bool {
        guard let url = url, let t = URL(string: target) else { return false }
        let host = AppConfig.stripWww((url.host ?? "").lowercased())
        guard host == AppConfig.stripWww((t.host ?? "").lowercased()), url.port == t.port else { return false }
        let here = url.path.hasSuffix("/") ? String(url.path.dropLast()) : url.path
        let key = t.path.hasSuffix("/") ? String(t.path.dropLast()) : t.path
        if key.isEmpty { return here.isEmpty }
        return here == key || here.hasPrefix(key + "/")
    }

    func hexString(_ color: UIColor?) -> String {
        guard let color = color else { return "" }
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.resolvedColor(with: traitCollection).getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "#%02X%02X%02X", Int(round(r * 255)), Int(round(g * 255)), Int(round(b * 255)))
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        return true
    }

    // MARK: - Scripts added to every page

    func addFeatureScripts(_ ucc: WKUserContentController) {
        func add(_ js: String, _ time: WKUserScriptInjectionTime) {
            ucc.addUserScript(WKUserScript(source: js, injectionTime: time, forMainFrameOnly: true))
        }
        if cfg.feature("haptics")?.flag("page", false) == true {
            ucc.add(self, name: "w2aTap")
            add("""
            (function(){if(window.__w2aTapOn)return;window.__w2aTapOn=1;document.addEventListener('click',function(e){
            var t=e.target;t=t&&t.closest?t.closest('a,button,[role=button],input[type=submit],input[type=button],summary,label'):null;
            if(t)window.webkit.messageHandlers.w2aTap.postMessage(1);},true);})();
            """, .atDocumentEnd)
        }
        if cfg.feature("notifyBridge") != nil {
            ucc.addScriptMessageHandler(self, contentWorld: .page, name: "w2aNotify")
            add("""
            (function(){if(window.Web2AppNotify)return;var h=window.webkit.messageHandlers.w2aNotify;
            var p=function(m){return h.postMessage(m);};var s=function(v){return v==null?'':String(v);};
            window.Web2AppNotify={schedule:function(t,x,sec,u){return p({a:'schedule',title:s(t),text:s(x),seconds:Number(sec)||0,url:s(u)});},
            cancel:function(id){return p({a:'cancel',id:Number(id)||0});},cancelAll:function(){return p({a:'cancelAll'});},
            pending:function(){return p({a:'pending'});}};})();
            """, .atDocumentStart)
        }
        if cfg.feature("longPress") != nil {
            // The app shows its own menu, so Safari's callout is turned off.
            add("""
            (function(){var s=document.createElement('style');s.textContent='a,img{-webkit-touch-callout:none}';
            (document.head||document.documentElement).appendChild(s);})();
            """, .atDocumentEnd)
        }
    }

    // MARK: - Page events

    func pageCommitted(_ url: URL?) {
        pagesShown += 1
        showToTop(false)
        updateBrightness(url)
        if let fade = cfg.feature("pageFade"), pagesShown > 1, lastNavType != .backForward {
            let seconds = Double(max(100, min(1000, fade.int("ms", 250)))) / 1000
            webView.alpha = 0
            UIView.animate(withDuration: seconds, delay: 0.04, options: [.curveEaseOut, .allowUserInteraction]) {
                self.webView.alpha = 1
            }
        }
    }

    func pageFinished() {
        triedCacheFor = nil
        cacheRetryURL = nil
        if offlineCopyShown || (isOffline && cfg.feature("offlineCache") != nil) {
            offlineCopyShown = false
            showSnack(cfg.feature("offlineCache")?.str("message", "You are offline. Showing a saved copy.") ?? "")
        }
        if cfg.feature("autoTheme") != nil { setPageTheme(webView.themeColor) }
    }

    // MARK: - Offline reading

    func setOffline(_ offline: Bool) {
        let was = isOffline
        isOffline = offline
        guard cfg.feature("offlineCache") != nil, was, !offline, !offlineView.isHidden else { return }
        // Back online while the "no connection" screen shows: try that page again.
        offlineView.isHidden = true
        webView.load(request(for: failedURL ?? cfg.homeURL))
    }

    /// Without a connection a page opens from the saved copy. Returns true when it was loaded that way instead.
    func useSavedCopy(_ action: WKNavigationAction) -> Bool {
        lastNavType = action.navigationType
        if let url = action.request.url, url == cacheRetryURL {
            cacheRetryURL = nil
            return false
        }
        guard isOffline, cfg.feature("offlineCache") != nil, action.request.cachePolicy != .returnCacheDataElseLoad,
              (action.request.httpMethod ?? "GET") == "GET", let url = action.request.url else { return false }
        var req = action.request
        req.cachePolicy = .returnCacheDataElseLoad
        cacheRetryURL = url
        offlineCopyShown = true
        webView.load(req)
        return true
    }

    /// The site could not be reached: one more try from the saved copy before the "no connection" screen.
    func retryFromCache(_ error: Error) -> Bool {
        guard cfg.feature("offlineCache") != nil else { return false }
        let ns = error as NSError
        let unreachable = [NSURLErrorNotConnectedToInternet, NSURLErrorCannotConnectToHost, NSURLErrorTimedOut,
                           NSURLErrorNetworkConnectionLost, NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed,
                           NSURLErrorDataNotAllowed, NSURLErrorInternationalRoamingOff]
        guard ns.domain == NSURLErrorDomain, unreachable.contains(ns.code),
              let url = (ns.userInfo[NSURLErrorFailingURLErrorKey] as? URL) ?? webView.url,
              triedCacheFor != url else {
            offlineCopyShown = false
            return false
        }
        triedCacheFor = url
        cacheRetryURL = url
        offlineCopyShown = true
        var req = URLRequest(url: url)
        req.cachePolicy = .returnCacheDataDontLoad
        webView.load(req)
        return true
    }

    // MARK: - Top bar tools: find in page, print, share

    func addToolButtons(_ stack: UIStackView, fg: UIColor, share: Bool, others: Int) {
        var tools: [(symbol: String, label: String, action: Selector)] = []
        if cfg.feature("findInPage") != nil { tools.append(("magnifyingglass", "Find in page", #selector(openFind))) }
        if cfg.feature("printPage") != nil { tools.append(("printer", "Print", #selector(printTapped))) }
        if share { tools.append(("square.and.arrow.up", "Share", #selector(shareTapped))) }
        // Too many buttons leave no room for the title: the tools go into a "More" menu.
        if tools.count >= 2 && others + tools.count > 5 {
            let more = barButton("ellipsis.circle", "More", #selector(noAction), tint: fg)
            more.menu = UIMenu(children: tools.map { tool in
                UIAction(title: tool.label, image: UIImage(systemName: tool.symbol)) { [weak self] _ in
                    _ = self?.perform(tool.action)
                }
            })
            more.showsMenuAsPrimaryAction = true
            stack.addArrangedSubview(more)
        } else {
            for tool in tools { stack.addArrangedSubview(barButton(tool.symbol, tool.label, tool.action, tint: fg)) }
        }
    }

    @objc func openFind() {
        tick()
        if #available(iOS 16.0, *), let find = webView.findInteraction {
            find.presentFindNavigator(showingReplace: false)
            return
        }
        let alert = UIAlertController(title: "Find in page", message: nil, preferredStyle: .alert)
        alert.addTextField { $0.returnKeyType = .search }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Find", style: .default) { [weak self, weak alert] _ in
            guard let self = self, let text = alert?.textFields?.first?.text, !text.isEmpty else { return }
            self.webView.evaluateJavaScript("window.find(\(WebViewController.jsString(text)))") { found, _ in
                if (found as? Bool) != true { self.showSnack("No matches") }
            }
        })
        present(alert, animated: true)
    }

    @objc func printTapped() { printPage() }

    func printPage() {
        tick()
        let info = UIPrintInfo(dictionary: nil)
        info.jobName = webView.title?.isEmpty == false ? webView.title! : cfg.appName
        info.outputType = .general
        let printer = UIPrintInteractionController.shared
        printer.printInfo = info
        printer.printFormatter = webView.viewPrintFormatter()
        printer.present(animated: true, completionHandler: nil)
    }

    // MARK: - Notice bar

    func loadNotice(_ options: [String: Any]) {
        guard let url = URL(string: options.str("checkUrl")) else { return }
        var req = URLRequest(url: url)
        req.cachePolicy = .reloadIgnoringLocalCacheData
        req.timeoutInterval = 12
        URLSession.shared.dataTask(with: req) { [weak self] data, _, _ in
            guard let data = data,
                  let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return }
            let message = json.str("message").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !message.isEmpty else { return }
            let id = json["id"].map { "\($0)" } ?? message
            if options.flag("dismissible", true), UserDefaults.standard.string(forKey: "w2a.noticeClosed") == id { return }
            DispatchQueue.main.async { self?.showNotice(options, id: id, message: message, link: json.str("link")) }
        }.resume()
    }

    func showNotice(_ options: [String: Any], id: String, message: String, link: String) {
        guard let holder = noticeHolder, holder.arrangedSubviews.isEmpty else { return }
        let bg = options.color("color") ?? theme
        let fg = bg.onColor
        let bar = UIView()
        bar.backgroundColor = bg
        bar.restorationIdentifier = id
        let text = UILabel()
        text.text = link.isEmpty ? message : message + "  ›"
        text.font = .systemFont(ofSize: 14, weight: .semibold)
        text.textColor = fg
        text.numberOfLines = 3
        text.translatesAutoresizingMaskIntoConstraints = false
        bar.addSubview(text)
        if let url = URL(string: link), !link.isEmpty {
            noticeLink = url
            bar.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(noticeTapped)))
            text.accessibilityTraits = .link
        }
        var trailing = bar.trailingAnchor
        if options.flag("dismissible", true) {
            let close = UIButton(type: .system)
            close.setImage(symbol("xmark", 14), for: .normal)
            close.tintColor = fg
            close.accessibilityLabel = "Close notice"
            close.addTarget(self, action: #selector(closeNotice), for: .touchUpInside)
            close.translatesAutoresizingMaskIntoConstraints = false
            bar.addSubview(close)
            NSLayoutConstraint.activate([
                close.trailingAnchor.constraint(equalTo: bar.safeAreaLayoutGuide.trailingAnchor, constant: -4),
                close.centerYAnchor.constraint(equalTo: bar.centerYAnchor),
                close.widthAnchor.constraint(equalToConstant: 44),
                close.heightAnchor.constraint(equalToConstant: 44),
            ])
            trailing = close.leadingAnchor
        }
        NSLayoutConstraint.activate([
            text.leadingAnchor.constraint(equalTo: bar.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            text.trailingAnchor.constraint(lessThanOrEqualTo: trailing, constant: -4),
            text.topAnchor.constraint(equalTo: bar.topAnchor, constant: 11),
            text.bottomAnchor.constraint(equalTo: bar.bottomAnchor, constant: -11),
            bar.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),
        ])
        bar.alpha = 0
        holder.addArrangedSubview(bar)
        UIView.animate(withDuration: 0.26) {
            bar.alpha = 1
            self.view.layoutIfNeeded()
        }
    }

    @objc func noticeTapped() {
        guard let url = noticeLink else { return }
        tick()
        openLink(url)
    }

    @objc func closeNotice() {
        guard let bar = noticeHolder?.arrangedSubviews.first else { return }
        tick()
        UserDefaults.standard.set(bar.restorationIdentifier, forKey: "w2a.noticeClosed")
        UIView.animate(withDuration: 0.2, animations: {
            bar.alpha = 0
            bar.isHidden = true
            self.view.layoutIfNeeded()
        }) { _ in bar.removeFromSuperview() }
    }

    // MARK: - Page colours (theme-color of each page)

    func setPageTheme(_ color: UIColor?) {
        guard let options = cfg.feature("autoTheme") else { return }
        var solid: UIColor?
        if let c = color {
            var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
            c.resolvedColor(with: traitCollection).getRed(&r, green: &g, blue: &b, alpha: &a)
            if a > 0.05 { solid = UIColor(red: r, green: g, blue: b, alpha: 1) }
        }
        if hexString(solid) == hexString(pageThemeColor) { return }
        pageThemeColor = solid
        UIView.animate(withDuration: 0.25) {
            if options.flag("topBar", true), let bar = self.topBar {
                bar.backgroundColor = solid ?? self.topDefaultBg ?? self.theme
                let fg = solid?.onColor ?? self.topDefaultFg
                for case let b as UIButton in self.topStack?.arrangedSubviews ?? [] { b.tintColor = fg }
                self.titleLabel?.textColor = fg
            }
            if options.flag("statusBar", true) {
                self.statusColor = solid ?? self.statusDefault
                self.view.backgroundColor = self.statusColor
            }
            self.setNeedsStatusBarAppearanceUpdate()
        }
    }

    // MARK: - Screen brightness on chosen pages

    func updateBrightness(_ url: URL?) {
        guard let options = cfg.feature("brightness") else { return }
        var match = false
        if let url = url, isInternal(url) {
            let path = url.path.isEmpty ? "/" : url.path
            for part in options.str("paths").split(separator: ",") {
                let p = part.trimmingCharacters(in: .whitespaces)
                if !p.isEmpty && path.hasPrefix(p) { match = true }
            }
        }
        guard match != brightOn else { return }
        brightOn = match
        applyBrightness(match)
    }

    /// iOS keeps the brightness an app sets after it closes, so it is put back whenever the app leaves the front.
    func applyBrightness(_ on: Bool) {
        let screen = view.window?.windowScene?.screen ?? UIScreen.main
        if on {
            if savedBrightness < 0 { savedBrightness = screen.brightness }
            let level = CGFloat(cfg.feature("brightness")?.int("level", 100) ?? 100) / 100
            screen.brightness = max(0.5, min(1, level))
        } else if savedBrightness >= 0 {
            screen.brightness = savedBrightness
            savedBrightness = -1
        }
    }

    // MARK: - Hide in app switcher

    func showPrivacyCover() {
        guard privacyCoverView == nil, let window = view.window else { return }
        let cover = UIView(frame: window.bounds)
        cover.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        cover.backgroundColor = theme
        cover.accessibilityIdentifier = "PrivacyCover"
        if let logo = UIImage(named: "SplashLogo") {
            let image = UIImageView(image: logo)
            image.frame = CGRect(x: 0, y: 0, width: 96, height: 96)
            image.center = CGPoint(x: cover.bounds.midX, y: cover.bounds.midY)
            image.autoresizingMask = [.flexibleLeftMargin, .flexibleRightMargin, .flexibleTopMargin, .flexibleBottomMargin]
            image.contentMode = .scaleAspectFill
            image.clipsToBounds = true
            image.layer.cornerRadius = 22
            cover.addSubview(image)
        }
        window.addSubview(cover)
        privacyCoverView = cover
        privacyCovers += 1
    }

    func hidePrivacyCover() {
        guard let cover = privacyCoverView else { return }
        privacyCoverView = nil
        UIView.animate(withDuration: 0.2, animations: { cover.alpha = 0 }) { _ in cover.removeFromSuperview() }
    }

    // MARK: - Side menu

    func buildMenuButton() {
        guard cfg.feature("drawer") != nil, topBar == nil else { return }
        let b = UIButton(type: .system)
        b.setImage(symbol("line.3.horizontal", 18), for: .normal)
        b.tintColor = theme.isLight ? .label : theme
        b.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.94)
        b.layer.cornerRadius = 22
        b.layer.shadowColor = UIColor.black.cgColor
        b.layer.shadowOpacity = 0.2
        b.layer.shadowRadius = 6
        b.layer.shadowOffset = CGSize(width: 0, height: 2)
        b.accessibilityLabel = "Menu"
        b.addTarget(self, action: #selector(openDrawer), for: .touchUpInside)
        b.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(b)
        NSLayoutConstraint.activate([
            b.widthAnchor.constraint(equalToConstant: 44),
            b.heightAnchor.constraint(equalToConstant: 44),
            b.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 12),
            b.topAnchor.constraint(equalTo: webView.topAnchor, constant: 10),
        ])
    }

    @objc func openDrawer() {
        guard drawerView == nil, let options = cfg.feature("drawer") else { return }
        tick()
        let overlay = UIView(frame: view.bounds)
        overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        overlay.accessibilityViewIsModal = true
        let scrim = UIButton(type: .custom)
        scrim.frame = overlay.bounds
        scrim.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        scrim.backgroundColor = UIColor(white: 0, alpha: 0.42)
        scrim.alpha = 0
        scrim.accessibilityLabel = "Close menu"
        scrim.addTarget(self, action: #selector(closeDrawerTapped), for: .touchUpInside)
        overlay.addSubview(scrim)

        let width = min(320, view.bounds.width * 0.82)
        let height = overlay.bounds.height
        let panel = UIView(frame: CGRect(x: -width, y: 0, width: width, height: height))
        panel.autoresizingMask = [.flexibleHeight]
        panel.backgroundColor = .systemBackground
        panel.layer.shadowColor = UIColor.black.cgColor
        panel.layer.shadowOpacity = 0.25
        panel.layer.shadowRadius = 16
        overlay.addSubview(panel)

        let safeTop = view.safeAreaInsets.top
        var y = safeTop + 12
        if options.flag("header", true) {
            let headerBg = options.color("headerColor") ?? theme
            let header = UIView(frame: CGRect(x: 0, y: 0, width: width, height: safeTop + 148))
            header.backgroundColor = headerBg
            if let logo = UIImage(named: "SplashLogo") {
                let image = UIImageView(image: logo)
                image.frame = CGRect(x: 20, y: safeTop + 18, width: 60, height: 60)
                image.contentMode = .scaleAspectFill
                image.clipsToBounds = true
                image.layer.cornerRadius = 15
                header.addSubview(image)
            }
            let name = UILabel(frame: CGRect(x: 20, y: safeTop + 94, width: width - 40, height: 32))
            name.text = cfg.appName
            name.font = .systemFont(ofSize: 21, weight: .bold)
            name.textColor = headerBg.onColor
            name.accessibilityTraits = .header
            header.addSubview(name)
            panel.addSubview(header)
            y = header.frame.maxY + 10
        }
        let scroll = UIScrollView(frame: CGRect(x: 0, y: y, width: width, height: height - y))
        scroll.autoresizingMask = [.flexibleHeight]
        panel.addSubview(scroll)
        let accent = theme.isLight ? UIColor.label : theme
        var rowY: CGFloat = 0
        for (i, item) in options.list("items").enumerated() {
            let selected = linkMatches(webView.url, item.str("url"))
            let row = UIButton(type: .custom)
            row.frame = CGRect(x: 10, y: rowY, width: width - 20, height: 50)
            row.layer.cornerRadius = 25
            row.tag = i
            row.backgroundColor = selected ? theme.withAlphaComponent(0.14) : .clear
            let icon = UIImageView(image: symbol(WebViewController.navSymbols[item.str("icon")] ?? "circle", 18, .regular))
            icon.frame = CGRect(x: 16, y: 13, width: 24, height: 24)
            icon.contentMode = .center
            icon.tintColor = selected ? accent : .secondaryLabel
            row.addSubview(icon)
            let label = UILabel(frame: CGRect(x: 54, y: 0, width: width - 84, height: 50))
            label.text = item.str("label")
            label.font = .systemFont(ofSize: 16, weight: selected ? .semibold : .medium)
            label.textColor = selected ? accent : .label
            row.addSubview(label)
            row.accessibilityLabel = item.str("label")
            row.accessibilityIdentifier = "drawer." + item.str("label")
            row.accessibilityTraits = selected ? [.button, .selected] : [.button]
            row.addTarget(self, action: #selector(drawerItemTapped(_:)), for: .touchUpInside)
            scroll.addSubview(row)
            rowY += 54
        }
        scroll.contentSize = CGSize(width: width, height: rowY + 20)
        overlay.addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(drawerPanned(_:))))

        view.addSubview(overlay)
        drawerView = overlay
        drawerPanel = panel
        UIView.animate(withDuration: 0.3, delay: 0, usingSpringWithDamping: 0.92, initialSpringVelocity: 0.4,
                       options: [.allowUserInteraction]) {
            panel.frame.origin.x = 0
            scrim.alpha = 1
        }
        UIAccessibility.post(notification: .screenChanged, argument: panel)
    }

    @objc func closeDrawerTapped() { closeDrawer(animated: true) }

    func closeDrawer(animated: Bool) {
        guard let overlay = drawerView, let panel = drawerPanel else { return }
        drawerView = nil
        drawerPanel = nil
        let scrim = overlay.subviews.first
        UIView.animate(withDuration: animated ? 0.22 : 0, delay: 0, options: [.curveEaseIn]) {
            panel.frame.origin.x = -panel.frame.width
            scrim?.alpha = 0
        } completion: { _ in overlay.removeFromSuperview() }
    }

    @objc func drawerItemTapped(_ sender: UIButton) {
        let items = cfg.feature("drawer")?.list("items") ?? []
        guard sender.tag < items.count, let url = URL(string: items[sender.tag].str("url")) else { return }
        tick()
        closeDrawer(animated: true)
        openLink(url)
    }

    @objc func drawerPanned(_ pan: UIPanGestureRecognizer) {
        guard let panel = drawerPanel, let scrim = drawerView?.subviews.first else { return }
        let dx = min(0, pan.translation(in: view).x)
        switch pan.state {
        case .changed:
            panel.frame.origin.x = dx
            scrim.alpha = 1 + dx / panel.frame.width
        case .ended, .cancelled:
            if dx < -panel.frame.width / 3 || pan.velocity(in: view).x < -600 {
                closeDrawer(animated: true)
            } else {
                UIView.animate(withDuration: 0.2) {
                    panel.frame.origin.x = 0
                    scrim.alpha = 1
                }
            }
        default:
            break
        }
    }

    // MARK: - Back to top button

    func buildToTop() {
        guard let options = cfg.feature("scrollTop") else { return }
        let left = options.str("position", "right") == "left"
        let fab = cfg.feature("fab")
        let sameSide = fab != nil && (fab?.str("position", "right") == "left") == left
        let b = UIButton(type: .system)
        b.setImage(symbol("arrow.up", 17, .bold), for: .normal)
        b.tintColor = theme.onColor
        b.backgroundColor = theme
        b.layer.cornerRadius = 22
        b.layer.shadowColor = UIColor.black.cgColor
        b.layer.shadowOpacity = 0.22
        b.layer.shadowRadius = 6
        b.layer.shadowOffset = CGSize(width: 0, height: 3)
        b.accessibilityLabel = "Back to top"
        b.alpha = 0
        b.isHidden = true
        b.addTarget(self, action: #selector(scrollToTopTapped), for: .touchUpInside)
        b.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(b)
        let guide = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            b.widthAnchor.constraint(equalToConstant: 44),
            b.heightAnchor.constraint(equalToConstant: 44),
            b.bottomAnchor.constraint(equalTo: navBar == nil ? guide.bottomAnchor : webView.bottomAnchor,
                                      constant: sameSide ? -(18 + 56 + 14) : -24),
            left ? b.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: 24)
                 : b.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -24),
        ])
        toTopButton = b
    }

    func scrolled(_ y: CGFloat) {
        guard let options = cfg.feature("scrollTop") else { return }
        showToTop(y > CGFloat(max(100, options.int("after", 800))))
    }

    func showToTop(_ show: Bool) {
        guard let b = toTopButton, show != toTopShown else { return }
        toTopShown = show
        if show {
            b.isHidden = false
            b.transform = CGAffineTransform(scaleX: 0.6, y: 0.6)
        }
        UIView.animate(withDuration: 0.2, animations: {
            b.alpha = show ? 1 : 0
            b.transform = show ? .identity : CGAffineTransform(scaleX: 0.6, y: 0.6)
        }) { _ in
            if !self.toTopShown { b.isHidden = true }
        }
    }

    @objc func scrollToTopTapped() {
        tick()
        let scroll = webView.scrollView
        scroll.setContentOffset(CGPoint(x: scroll.contentOffset.x, y: -scroll.adjustedContentInset.top), animated: true)
    }

    // MARK: - Start screens: age check and privacy consent

    func buildGate() {
        guard gateView == nil else { return }
        let defaults = UserDefaults.standard
        if let age = cfg.feature("ageGate"), !defaults.bool(forKey: "w2a.ageOk") {
            showAgeGate(age)
        } else if let consent = cfg.feature("consent"),
                  defaults.integer(forKey: "w2a.consentVersion") < consent.int("version", 1) {
            showConsent(consent)
        }
    }

    private func gateScreen(_ title: String, _ message: String) -> UIStackView {
        let overlay = UIView()
        overlay.backgroundColor = theme
        overlay.accessibilityViewIsModal = true
        overlay.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(overlay)
        NSLayoutConstraint.activate([
            overlay.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlay.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlay.topAnchor.constraint(equalTo: view.topAnchor),
            overlay.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        let fg = theme.onColor
        let stack = UIStackView()
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        if let logo = UIImage(named: "SplashLogo") {
            let image = UIImageView(image: logo)
            image.contentMode = .scaleAspectFill
            image.clipsToBounds = true
            image.layer.cornerRadius = 22
            image.translatesAutoresizingMaskIntoConstraints = false
            image.widthAnchor.constraint(equalToConstant: 92).isActive = true
            image.heightAnchor.constraint(equalToConstant: 92).isActive = true
            stack.addArrangedSubview(image)
            stack.setCustomSpacing(30, after: image)
        }
        let head = UILabel()
        head.text = title
        head.font = .systemFont(ofSize: 26, weight: .bold)
        head.textColor = fg
        head.numberOfLines = 0
        head.textAlignment = .center
        head.accessibilityTraits = .header
        head.tag = 1
        stack.addArrangedSubview(head)
        let body = UILabel()
        body.text = message
        body.font = .systemFont(ofSize: 16)
        body.textColor = fg.withAlphaComponent(0.86)
        body.numberOfLines = 0
        body.textAlignment = .center
        body.tag = 2
        stack.addArrangedSubview(body)
        stack.setCustomSpacing(30, after: body)
        overlay.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: overlay.centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: overlay.centerYAnchor, constant: -10),
            stack.leadingAnchor.constraint(greaterThanOrEqualTo: overlay.safeAreaLayoutGuide.leadingAnchor, constant: 32),
            stack.trailingAnchor.constraint(lessThanOrEqualTo: overlay.safeAreaLayoutGuide.trailingAnchor, constant: -32),
            stack.widthAnchor.constraint(lessThanOrEqualToConstant: 440),
        ])
        gateView = overlay
        return stack
    }

    private func pillButton(_ title: String) -> UIButton {
        let fg = theme.onColor
        let b = UIButton(type: .system)
        b.setTitle(title, for: .normal)
        b.setTitleColor(theme, for: .normal)
        b.backgroundColor = fg
        b.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        b.layer.cornerRadius = 26
        b.contentEdgeInsets = UIEdgeInsets(top: 0, left: 28, bottom: 0, right: 28)
        b.translatesAutoresizingMaskIntoConstraints = false
        b.heightAnchor.constraint(equalToConstant: 52).isActive = true
        b.widthAnchor.constraint(greaterThanOrEqualToConstant: 230).isActive = true
        return b
    }

    private func showAgeGate(_ options: [String: Any]) {
        let age = max(1, options.int("age", 18))
        let stack = gateScreen("Are you \(age) or older?", "\(cfg.appName) is for people aged \(age) and over.")
        let yes = pillButton("Yes, I am \(age)+")
        let no = UIButton(type: .system)
        no.setTitle("No", for: .normal)
        no.setTitleColor(theme.onColor, for: .normal)
        no.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        no.contentEdgeInsets = UIEdgeInsets(top: 12, left: 28, bottom: 12, right: 28)
        yes.addAction(UIAction { [weak self] _ in
            self?.tick()
            UserDefaults.standard.set(true, forKey: "w2a.ageOk")
            self?.closeGate()
        }, for: .touchUpInside)
        no.addAction(UIAction { [weak self, weak stack, weak yes, weak no] _ in
            guard let self = self, let stack = stack else { return }
            self.tick()
            (stack.viewWithTag(1) as? UILabel)?.text = self.cfg.appName
            (stack.viewWithTag(2) as? UILabel)?.text = options.str("blocked", "Sorry, this app is not available for your age.")
            yes?.removeFromSuperview()
            no?.removeFromSuperview()
            UIAccessibility.post(notification: .screenChanged, argument: stack)
        }, for: .touchUpInside)
        stack.addArrangedSubview(yes)
        stack.setCustomSpacing(6, after: yes)
        stack.addArrangedSubview(no)
    }

    private func showConsent(_ options: [String: Any]) {
        let stack = gateScreen(options.str("title", "Your privacy"), options.str("message"))
        if let policy = URL(string: options.str("policyUrl")), ["http", "https"].contains(policy.scheme?.lowercased() ?? "") {
            let link = UIButton(type: .system)
            link.setAttributedTitle(NSAttributedString(string: "Read the privacy policy", attributes: [
                .font: UIFont.systemFont(ofSize: 15, weight: .semibold),
                .foregroundColor: theme.onColor,
                .underlineStyle: NSUnderlineStyle.single.rawValue,
            ]), for: .normal)
            link.contentEdgeInsets = UIEdgeInsets(top: 10, left: 16, bottom: 10, right: 16)
            link.addAction(UIAction { [weak self] _ in
                guard let self = self else { return }
                // The policy is read on top of this screen; the app stays closed until it is accepted.
                let safari = SFSafariViewController(url: policy)
                safari.dismissButtonStyle = .close
                if !self.theme.isLight { safari.preferredControlTintColor = self.theme }
                self.topController.present(safari, animated: true)
            }, for: .touchUpInside)
            stack.addArrangedSubview(link)
            stack.setCustomSpacing(18, after: link)
        }
        let accept = pillButton(options.str("accept", "Accept"))
        accept.addAction(UIAction { [weak self] _ in
            self?.tick()
            UserDefaults.standard.set(options.int("version", 1), forKey: "w2a.consentVersion")
            self?.closeGate()
        }, for: .touchUpInside)
        stack.addArrangedSubview(accept)
    }

    func closeGate() {
        guard let gate = gateView else { return }
        gateView = nil
        UIView.animate(withDuration: 0.25, animations: { gate.alpha = 0 }) { _ in gate.removeFromSuperview() }
        gateClosed()
    }

    // MARK: - Announcement

    func maybeShowAnnouncement() {
        guard let options = cfg.feature("announcement") else { return }
        let version = options.int("version", 1)
        let defaults = UserDefaults.standard
        guard defaults.integer(forKey: "w2a.annVersion") < version else { return }
        // Wait for permission questions and other windows to go away first.
        if presentedViewController != nil || drawerView != nil || UIApplication.shared.applicationState != .active {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in self?.maybeShowAnnouncement() }
            return
        }
        defaults.set(version, forKey: "w2a.annVersion")
        let alert = UIAlertController(title: options.str("title", "What's new"), message: options.str("message"),
                                      preferredStyle: .alert)
        let close = options.str("button", "Got it")
        if let url = URL(string: options.str("link")), !options.str("link").isEmpty {
            alert.addAction(UIAlertAction(title: close, style: .cancel))
            alert.addAction(UIAlertAction(title: options.str("linkLabel", "Learn more"), style: .default) { [weak self] _ in
                self?.openLink(url)
            })
        } else {
            alert.addAction(UIAlertAction(title: close, style: .default))
        }
        present(alert, animated: true)
    }

    // MARK: - Shake

    func onShake() {
        guard let options = cfg.feature("shake"), lockView == nil, gateView == nil, presentedViewController == nil else { return }
        tick(force: true)
        switch options.str("action", "reload") {
        case "home":
            webView.load(request(for: cfg.homeURL))
        case "feedback":
            let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
            let device = UIDevice.current
            let body = "\n\n---\nPage: \(webView.url?.absoluteString ?? "")\nApp: \(cfg.appName) \(version)\n"
                + "\(device.systemName) \(device.systemVersion), \(device.model)"
            var mail = URLComponents()
            mail.scheme = "mailto"
            mail.path = options.str("email")
            mail.queryItems = [URLQueryItem(name: "subject", value: "Feedback: \(cfg.appName)"),
                               URLQueryItem(name: "body", value: body)]
            if let url = mail.url {
                UIApplication.shared.open(url) { [weak self] ok in
                    if !ok { self?.showSnack("No email app found") }
                }
            }
        default:
            showSnack("Reloading…")
            webView.reload()
        }
    }

    // MARK: - Idle reset (kiosk)

    func idleCheck() {
        guard let options = cfg.feature("idleReset"), UIApplication.shared.applicationState == .active else { return }
        let limit = Double(max(1, options.int("minutes", 5)) * 60)
        guard Date().timeIntervalSince(lastTouch) >= limit else { return }
        lastTouch = Date()
        guard lockView == nil, gateView == nil else { return }
        closeDrawer(animated: false)
        if presentedViewController != nil { dismiss(animated: false) }
        let home = { [weak self] in
            guard let self = self else { return }
            self.webView.load(self.request(for: self.cfg.homeURL))
        }
        let trim = { (u: URL?) -> String in
            let s = u?.absoluteString ?? ""
            return s.hasSuffix("/") ? String(s.dropLast()) : s
        }
        if options.flag("clear", false) {
            UserDefaults.standard.removeObject(forKey: "w2a.lastPage")
            WKWebsiteDataStore.default().removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(),
                                                    modifiedSince: .distantPast) { home() }
        } else if trim(webView.url) != trim(cfg.homeURL) {
            home()
        } else {
            scrollToTopTapped()
        }
    }

    // MARK: - Long-press menu for links and images

    @objc func longPressed(_ press: UILongPressGestureRecognizer) {
        guard press.state == .began else { return }
        let point = press.location(in: webView)
        let js = """
        (function(x,y,w){var s=window.innerWidth/w;var e=document.elementFromPoint(x*s,y*s);if(!e||!e.closest)return null;
        var a=e.closest('a[href]');var i=e.closest('img');
        return {link:a?a.href:'',img:i?(i.currentSrc||i.src||''):''};})(\(point.x),\(point.y),\(webView.bounds.width))
        """
        webView.evaluateJavaScript(js) { [weak self] result, _ in
            guard let self = self, let info = result as? [String: Any] else { return }
            let link = info.str("link").isEmpty ? nil : URL(string: info.str("link"))
            let image = info.str("img").isEmpty ? nil : URL(string: info.str("img"))
            guard link != nil || image != nil else { return }
            self.showLinkMenu(link: link, image: image, at: point)
        }
    }

    func showLinkMenu(link: URL?, image: URL?, at point: CGPoint) {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        let sheet = UIAlertController(title: nil, message: (link ?? image)?.absoluteString, preferredStyle: .actionSheet)
        if let image = image {
            if cfg.feature("longPress")?.flag("saveImages", true) != false {
                sheet.addAction(UIAlertAction(title: "Save image", style: .default) { [weak self] _ in self?.saveImage(image) })
            }
            sheet.addAction(UIAlertAction(title: "Share image", style: .default) { [weak self] _ in
                self?.fetchImage(image) { picture in
                    if let picture = picture { self?.share(items: [picture]) } else { self?.share(items: [image]) }
                }
            })
        }
        if let link = link {
            sheet.addAction(UIAlertAction(title: "Open link", style: .default) { [weak self] _ in self?.openLink(link) })
            sheet.addAction(UIAlertAction(title: "Open in browser", style: .default) { _ in UIApplication.shared.open(link) })
            sheet.addAction(UIAlertAction(title: "Copy link", style: .default) { [weak self] _ in
                UIPasteboard.general.url = link
                self?.showSnack("Link copied")
            })
            sheet.addAction(UIAlertAction(title: "Share link", style: .default) { [weak self] _ in self?.share(items: [link]) })
        }
        sheet.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        sheet.popoverPresentationController?.sourceView = webView
        sheet.popoverPresentationController?.sourceRect = CGRect(origin: point, size: CGSize(width: 1, height: 1))
        topController.present(sheet, animated: true)
    }

    func fetchImage(_ url: URL, _ done: @escaping (UIImage?) -> Void) {
        URLSession.shared.dataTask(with: url) { data, _, _ in
            let image = data.flatMap { UIImage(data: $0) }
            DispatchQueue.main.async { done(image) }
        }.resume()
    }

    func saveImage(_ url: URL) {
        fetchImage(url) { [weak self] image in
            guard let self = self else { return }
            guard let image = image else {
                self.showSnack("Could not load the image")
                return
            }
            self.systemPromptExpected = true
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                DispatchQueue.main.async {
                    guard status == .authorized || status == .limited else {
                        self.showSnack("Allow access to Photos in Settings to save pictures")
                        return
                    }
                    PHPhotoLibrary.shared().performChanges({
                        PHAssetChangeRequest.creationRequestForAsset(from: image)
                    }) { ok, _ in
                        DispatchQueue.main.async { self.showSnack(ok ? "Saved to Photos" : "Could not save the image") }
                    }
                }
            }
        }
    }

    // MARK: - Local notifications from the page (Web2AppNotify)

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage,
                               replyHandler: @escaping (Any?, String?) -> Void) {
        guard message.name == "w2aNotify", let options = cfg.feature("notifyBridge"),
              let page = message.frameInfo.request.url, isInternal(page),
              let body = message.body as? [String: Any] else {
            replyHandler(-1, nil)
            return
        }
        let center = UNUserNotificationCenter.current()
        let mine = { (done: @escaping ([String]) -> Void) in
            center.getPendingNotificationRequests { pending in
                let ids = pending.map { $0.identifier }.filter { $0.hasPrefix("w2a.n.") }
                DispatchQueue.main.async { done(ids) }
            }
        }
        switch body.str("a") {
        case "schedule":
            let title = String(body.str("title").trimmingCharacters(in: .whitespacesAndNewlines).prefix(80))
            let seconds = (body["seconds"] as? NSNumber)?.doubleValue ?? 0
            guard !title.isEmpty, seconds >= 0 else {
                replyHandler(-1, nil)
                return
            }
            var link = body.str("url")
            if let u = URL(string: link), ["http", "https"].contains(u.scheme?.lowercased() ?? ""), isInternal(u) {} else { link = "" }
            systemPromptExpected = true
            center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                mine { ids in
                    guard granted, ids.count < max(1, options.int("max", 20)) else {
                        replyHandler(-1, nil)
                        return
                    }
                    let defaults = UserDefaults.standard
                    let id = max(100, defaults.integer(forKey: "w2a.notifyNext"))
                    defaults.set(id + 1 >= 1_000_000 ? 100 : id + 1, forKey: "w2a.notifyNext")
                    let content = UNMutableNotificationContent()
                    content.title = title
                    content.body = String(body.str("text").prefix(300))
                    content.sound = .default
                    content.threadIdentifier = "w2a.local"
                    content.userInfo = ["url": link]
                    let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, seconds), repeats: false)
                    center.add(UNNotificationRequest(identifier: "w2a.n.\(id)", content: content, trigger: trigger)) { error in
                        DispatchQueue.main.async { replyHandler(error == nil ? id : -1, nil) }
                    }
                }
            }
        case "cancel":
            let id = (body["id"] as? NSNumber)?.intValue ?? 0
            center.removePendingNotificationRequests(withIdentifiers: ["w2a.n.\(id)"])
            replyHandler(true, nil)
        case "cancelAll":
            mine { ids in
                center.removePendingNotificationRequests(withIdentifiers: ids)
                replyHandler(true, nil)
            }
        case "pending":
            mine { ids in replyHandler(ids.count, nil) }
        default:
            replyHandler(-1, nil)
        }
    }

    // MARK: - Automated tests only

    func selfTestFeatures() -> [String: Any] {
        return [
            "gate": gateView != nil,
            "drawer": drawerView != nil,
            "notice": noticeHolder?.arrangedSubviews.first != nil,
            "toTop": toTopShown,
            "pageTheme": hexString(pageThemeColor),
            "topBar": hexString(topBar?.backgroundColor),
            "status": hexString(view.backgroundColor),
            "bright": brightOn,
            "brightness": Double(round((view.window?.windowScene?.screen ?? UIScreen.main).brightness * 100) / 100),
            "offline": isOffline,
            "covers": privacyCovers,
            "audio": AVAudioSession.sharedInstance().category.rawValue,
            "shakeToEdit": UIApplication.shared.applicationSupportsShakeToEdit,
            "idleMinutes": cfg.feature("idleReset")?.int("minutes", 5) ?? 0,
            "rateAsks": UserDefaults.standard.integer(forKey: "w2a.rateAsks"),
        ]
    }

    /// A tiny label the UI tests read; only in test runs.
    func updateSelfTestLabel() {
        guard let window = view.window else { return }
        var label = window.viewWithTag(4242) as? UILabel
        if label == nil {
            let l = UILabel(frame: CGRect(x: 0, y: 0, width: 2, height: 2))
            l.tag = 4242
            l.alpha = 0.02
            l.isAccessibilityElement = true
            l.accessibilityIdentifier = "w2a-selftest"
            window.addSubview(l)
            label = l
        }
        window.bringSubviewToFront(label!)
        if let data = try? JSONSerialization.data(withJSONObject: selfTestFeatures(), options: [.sortedKeys]),
           let text = String(data: data, encoding: .utf8) {
            label?.accessibilityLabel = text
        }
    }
}

/// Notices every touch on the screen without getting in its way (idle reset).
final class TouchWatcher: UIGestureRecognizer {
    private let onTouch: () -> Void

    init(_ onTouch: @escaping () -> Void) {
        self.onTouch = onTouch
        super.init(target: nil, action: nil)
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        onTouch()
        state = .failed
    }
}
