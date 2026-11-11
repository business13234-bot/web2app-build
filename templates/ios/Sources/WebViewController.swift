import UIKit
import WebKit
import AVFoundation
import CoreLocation
import Contacts
import Photos
import UserNotifications
import AppTrackingTransparency
import LocalAuthentication
import StoreKit
import Network
import SafariServices

final class WebViewController: UIViewController, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler,
                               WKScriptMessageHandlerWithReply, UIScrollViewDelegate, UIGestureRecognizerDelegate {

    /// The controller on screen, so a tapped notification can open its page.
    static weak var current: WebViewController?
    /// A page to open first, from a notification tapped while the app was closed.
    static var launchURL: URL?

    let cfg = AppConfig.shared
    var webView: WKWebView!
    private let progressView = UIProgressView(progressViewStyle: .bar)
    let offlineView = UIView()
    var observations: [NSKeyValueObservation] = []
    var failedURL: URL?
    private let locationManager = CLLocationManager()
    var theme: UIColor = .systemBlue
    var statusColor: UIColor = .systemBlue

    /// Page to open first (deep link), set before the view loads.
    var pendingURL: URL?

    // Features
    var topBar: UIView?
    var titleLabel: UILabel?
    private var backButton: UIButton?
    var navBar: UIView?
    var navButtons: [UIButton] = []
    var navItems: [[String: Any]] = []
    var navActive: UIColor = .systemBlue
    private var spinner: UIActivityIndicatorView?
    var splashView: UIView?
    private var splashVideoPlaying = false
    private var splashVideoEnd: NSObjectProtocol?
    private var splashStarted = Date()
    private var firstPageDone = false
    var lockView: UIView?
    var unlocking = false
    private var lockButton: UIButton?
    private var unlockWhenActive = false
    private var leftAt: Date?
    var onboardingView: UIView?
    private var onboardingScroll: UIScrollView?
    private var onboardingDots: UIPageControl?
    private var onboardingButton: UIButton?
    private var snack: UIView?
    private var pathMonitor: NWPathMonitor?
    private var wasOffline = false
    private var bridgeName = ""
    private var startupDone = false

    // Features added in 1.2
    var webTopConstraint: NSLayoutConstraint?
    var topStack: UIStackView?
    var topDefaultBg: UIColor?
    var topDefaultFg: UIColor = .white
    var statusDefault: UIColor = .systemBlue
    var pageThemeColor: UIColor?
    var noticeHolder: UIStackView?
    var drawerView: UIView?
    var drawerPanel: UIView?
    var toTopButton: UIButton?
    var toTopShown = false
    var gateView: UIView?
    var privacyCoverView: UIView?
    var promptUntil = Date.distantPast
    var noticeLink: URL?
    var lastNavType: WKNavigationType = .other
    var triedCacheFor: URL?
    var privacyCovers = 0
    var firstLoadStarted = false
    var lastTouch = Date()
    var idleTimer: Timer?
    var isOffline = false
    var offlineCopyShown = false
    var cacheRetryURL: URL?
    var brightOn = false
    var savedBrightness: CGFloat = -1
    var pagesShown = 0

    // Features added in 1.4
    var errorOverlay: UIView?
    var httpErrorURL: URL?
    var speaker: AVSpeechSynthesizer?
    var dataSaverRules: WKContentRuleList?
    var dataSaverMonitor: NWPathMonitor?
    var dataSaverActive = false
    var backgroundedAt: Date?

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()

        theme = cfg.color("themeColor", "#1565C0")
        let bars = cfg.feature("systemBars")
        let top = cfg.feature("topBar")
        statusColor = bars?.color("status") ?? top?.color("bg") ?? theme
        view.backgroundColor = statusColor

        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        let suffix = cfg.string("userAgentSuffix")
        if !suffix.isEmpty { configuration.applicationNameForUserAgent = suffix }
        if cfg.feature("desktopMode") != nil {
            configuration.defaultWebpagePreferences.preferredContentMode = .desktop
        }
        if cfg.isLocal { configuration.setURLSchemeHandler(LocalFiles(), forURLScheme: AppConfig.localScheme) }
        addUserScripts(configuration.userContentController)

        webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.translatesAutoresizingMaskIntoConstraints = false
        webView.isOpaque = false
        webView.backgroundColor = .white
        if cfg.feature("noSelect") != nil || cfg.feature("longPress") != nil { webView.allowsLinkPreview = false }
        if #available(iOS 16.0, *), cfg.feature("findInPage") != nil { webView.isFindInteractionEnabled = true }
        let userAgent = cfg.string("userAgent")
        if !userAgent.isEmpty { webView.customUserAgent = userAgent }
        view.addSubview(webView)

        layoutBars()

        if cfg.bool("pullToRefresh", true) {
            let refresh = UIRefreshControl()
            refresh.tintColor = theme
            refresh.addTarget(self, action: #selector(reloadPage(_:)), for: .valueChanged)
            webView.scrollView.refreshControl = refresh
        }

        buildLoader()
        buildOfflineView()
        buildFab()
        buildToTop()
        buildMenuButton()
        buildOnboarding()
        buildGate()
        buildLock()
        buildSplash()
        startFeatures()

        if cfg.bool("keepScreenOn", false) {
            UIApplication.shared.isIdleTimerDisabled = true
        }

        observations.append(webView.observe(\.title, options: [.new]) { [weak self] web, _ in
            self?.updateTitle(web.title)
        })
        observations.append(webView.observe(\.canGoBack, options: [.new]) { [weak self] _, _ in
            self?.updateBackButton()
        })

        NotificationCenter.default.addObserver(self, selector: #selector(didEnterBackground),
                                               name: UIApplication.didEnterBackgroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(willEnterForeground),
                                               name: UIApplication.willEnterForegroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(didBecomeActive),
                                               name: UIApplication.didBecomeActiveNotification, object: nil)
        startNetworkBanner()

        prepareData { [weak self] in
            guard let self = self else { return }
            self.firstLoadStarted = true
            self.webView.load(self.request(for: self.startURL()))
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if cfg.feature("shake") != nil { becomeFirstResponder() }
        if splashView == nil { afterSplash() }
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        switch cfg.feature("systemBars")?.str("icons", "auto") ?? "auto" {
        case "light": return .lightContent
        case "dark": return .darkContent
        default:
            if splashView != nil { return cfg.color("splashColor", "#FFFFFF").isLight ? .darkContent : .lightContent }
            return statusColor.isLight ? .darkContent : .lightContent
        }
    }

    // Shake the phone (shake action feature).
    override var canBecomeFirstResponder: Bool { return cfg.feature("shake") != nil }

    override func motionEnded(_ motion: UIEvent.EventSubtype, with event: UIEvent?) {
        if motion == .motionShake, cfg.feature("shake") != nil { onShake() } else { super.motionEnded(motion, with: event) }
    }

    override var prefersStatusBarHidden: Bool {
        return cfg.bool("fullscreen", false)
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        switch cfg.string("orientation", "auto") {
        case "portrait": return .portrait
        case "landscape": return .landscape
        default: return .all
        }
    }

    func request(for url: URL) -> URLRequest {
        var req = URLRequest(url: url)
        if !cfg.bool("cache", true) { req.cachePolicy = .reloadIgnoringLocalCacheData }
        // Offline reading: use the saved copy of a page when there is no connection.
        if isOffline, cfg.feature("offlineCache") != nil { req.cachePolicy = .returnCacheDataElseLoad }
        return req
    }

    private func startURL() -> URL {
        if let url = pendingURL { return url }
        if let url = WebViewController.launchURL {
            WebViewController.launchURL = nil
            return url
        }
        if cfg.feature("rememberPage") != nil,
           let saved = UserDefaults.standard.string(forKey: "w2a.lastPage"),
           let url = URL(string: saved), isInternal(url) {
            return url
        }
        return cfg.homeURL
    }

    /// Opens a page from a deep link while the app is running.
    func open(_ url: URL) {
        if webView == nil || !firstLoadStarted {
            pendingURL = url
            return
        }
        webView.load(request(for: url))
    }

    /// myapp://shop/42 -> https://your-site/shop/42 ; website links stay as they are.
    static func pageURL(forDeepLink url: URL) -> URL? {
        let scheme = (url.scheme ?? "").lowercased()
        if scheme == "http" || scheme == "https" { return url }
        guard var comps = URLComponents(url: AppConfig.shared.homeURL, resolvingAgainstBaseURL: false) else { return nil }
        var path = ""
        if let host = url.host, !host.isEmpty { path += "/" + host }
        path += url.path
        comps.path = path.isEmpty ? "/" : path
        comps.query = url.query
        comps.fragment = url.fragment
        return comps.url
    }

    /// Clears data and compiles the content blocker before the first page loads.
    private func prepareData(_ done: @escaping () -> Void) {
        let group = DispatchGroup()
        if let clear = cfg.feature("clearOnStart") {
            group.enter()
            let types: Set<String> = clear.str("what", "cache") == "all"
                ? WKWebsiteDataStore.allWebsiteDataTypes()
                : [WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache]
            WKWebsiteDataStore.default().removeData(ofTypes: types, modifiedSince: Date.distantPast) {
                group.leave()
            }
        }
        if let blocker = cfg.feature("blocker") {
            let hosts = blocker.strings("hosts")
            if !hosts.isEmpty {
                var rules: [[String: Any]] = []
                for host in hosts {
                    let h = NSRegularExpression.escapedPattern(for: host.lowercased())
                    for filter in ["^[a-z]+://\(h)[:/]", "^[a-z]+://[a-z0-9.-]*\\.\(h)[:/]"] {
                        rules.append(["trigger": ["url-filter": filter], "action": ["type": "block"]])
                    }
                }
                if let data = try? JSONSerialization.data(withJSONObject: rules),
                   let json = String(data: data, encoding: .utf8) {
                    group.enter()
                    WKContentRuleListStore.default().compileContentRuleList(
                        forIdentifier: "w2a-blocker", encodedContentRuleList: json) { [weak self] list, _ in
                        if let list = list { self?.webView.configuration.userContentController.add(list) }
                        group.leave()
                    }
                }
            }
        }
        group.notify(queue: .main, execute: done)
    }

    @objc private func reloadPage(_ sender: UIRefreshControl) {
        if webView.url != nil {
            webView.reload()
        } else {
            webView.load(request(for: failedURL ?? cfg.homeURL))
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { sender.endRefreshing() }
    }

    // MARK: - Page scripts (zoom, CSS, JavaScript, text size, native feel, bridge)

    static func jsString(_ s: String) -> String {
        if let data = try? JSONSerialization.data(withJSONObject: [s]),
           let text = String(data: data, encoding: .utf8) {
            return String(text.dropFirst().dropLast())
        }
        return "\"\""
    }

    private func addUserScripts(_ ucc: WKUserContentController) {
        func add(_ js: String, _ time: WKUserScriptInjectionTime) {
            ucc.addUserScript(WKUserScript(source: js, injectionTime: time, forMainFrameOnly: true))
        }
        func style(_ css: String) -> String {
            return "(function(){var s=document.createElement('style');s.textContent=\(WebViewController.jsString(css));"
                + "(document.head||document.documentElement).appendChild(s);})();"
        }
        if cfg.feature("desktopMode") != nil {
            // Desktop layout: a wide page the user can zoom into.
            add("""
            (function(){var m=document.querySelector('meta[name=viewport]');
            if(!m){m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m);}
            m.content='width=1200';})();
            """, .atDocumentEnd)
        } else if !cfg.bool("zoom", false) {
            add("""
            (function(){var m=document.querySelector('meta[name=viewport]');
            if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}
            m.content='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';})();
            """, .atDocumentEnd)
        }
        if cfg.feature("noSelect") != nil {
            add(style("*{-webkit-user-select:none;user-select:none;-webkit-touch-callout:none;"
                + "-webkit-tap-highlight-color:transparent}input,textarea,[contenteditable],[contenteditable] *"
                + "{-webkit-user-select:text;user-select:text}"), .atDocumentEnd)
        }
        if let size = cfg.feature("textSize") {
            let pct = max(50, min(200, size.int("percent", 100)))
            add("document.documentElement.style.webkitTextSizeAdjust='\(pct)%';", .atDocumentEnd)
        }
        if let css = cfg.feature("customCss")?.str("css"), !css.isEmpty {
            add(style(css), .atDocumentEnd)
        }
        if let js = cfg.feature("customJs")?.str("js"), !js.isEmpty {
            add("try{\n\(js)\n}catch(e){console.error(e)}", .atDocumentEnd)
        }
        addFeatureScripts(ucc)
        addPageFeatures(ucc)
        if let bridge = cfg.feature("jsBridge") {
            bridgeName = bridge.str("name", "Web2App")
            let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
            ucc.add(self, name: "w2a")
            add("""
            (function(){var n=\(WebViewController.jsString(bridgeName));if(window[n])return;
            var post=function(a,v){window.webkit.messageHandlers.w2a.postMessage({action:a,value:v==null?'':String(v)});};
            window[n]={platform:function(){return 'ios';},appVersion:function(){return \(WebViewController.jsString(version));},
            pushToken:function(){return '';},share:function(t){post('share',t);},toast:function(t){post('toast',t);},
            copy:function(t){post('copy',t);},vibrate:function(ms){post('vibrate',ms);},print:function(){post('print');},
            openExternal:function(u){post('openExternal',u);}};})();
            """, .atDocumentStart)
        }
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if message.name == "w2aTap" {
            tick()
            return
        }
        if message.name == "w2aSpeech" {
            handleSpeech(message)
            return
        }
        // Only pages of your own website may use the bridge.
        guard message.name == "w2a",
              let url = message.frameInfo.request.url, isInternal(url),
              let body = message.body as? [String: Any],
              let action = body["action"] as? String else { return }
        let value = body["value"] as? String ?? ""
        switch action {
        case "share":
            share(items: [value.isEmpty ? (webView.url?.absoluteString ?? "") : value])
        case "toast":
            showSnack(value)
        case "copy":
            UIPasteboard.general.string = value
            showSnack("Copied")
        case "vibrate":
            let gen = UIImpactFeedbackGenerator(style: (Int(value) ?? 50) > 150 ? .heavy : .medium)
            gen.impactOccurred()
        case "print":
            printPage()
        case "openExternal":
            if let target = URL(string: value) { UIApplication.shared.open(target) }
        default:
            break
        }
    }

    func share(items: [Any]) {
        let sheet = UIActivityViewController(activityItems: items, applicationActivities: nil)
        sheet.popoverPresentationController?.sourceView = view
        sheet.popoverPresentationController?.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
        present(sheet, animated: true)
    }

    // MARK: - Top bar, bottom navigation and layout

    func symbol(_ name: String, _ size: CGFloat = 20, _ weight: UIImage.SymbolWeight = .semibold) -> UIImage? {
        return UIImage(systemName: name, withConfiguration: UIImage.SymbolConfiguration(pointSize: size, weight: weight))
    }

    static let navSymbols: [String: String] = [
        "home": "house.fill", "search": "magnifyingglass", "shopping_cart": "cart.fill", "person": "person.fill",
        "favorite": "heart.fill", "notifications": "bell.fill", "menu": "line.3.horizontal", "chat": "bubble.left.fill",
        "info": "info.circle.fill", "star": "star.fill", "settings": "gearshape.fill", "call": "phone.fill",
        "mail": "envelope.fill", "share": "square.and.arrow.up", "bookmark": "bookmark.fill",
        "location_on": "mappin.circle.fill", "grid_view": "square.grid.2x2.fill", "play_circle": "play.circle.fill",
    ]

    func barButton(_ symbolName: String, _ label: String, _ action: Selector, tint: UIColor) -> UIButton {
        let b = UIButton(type: .system)
        b.setImage(symbol(symbolName), for: .normal)
        b.tintColor = tint
        b.accessibilityLabel = label
        b.addTarget(self, action: action, for: .touchUpInside)
        if cfg.feature("haptics") != nil { b.addTarget(self, action: #selector(tickTap), for: .touchUpInside) }
        b.translatesAutoresizingMaskIntoConstraints = false
        b.widthAnchor.constraint(equalToConstant: 44).isActive = true
        return b
    }

    private func layoutBars() {
        let guide = view.safeAreaLayoutGuide
        let fullscreen = cfg.bool("fullscreen", false)
        var webTop = fullscreen ? view.topAnchor : guide.topAnchor
        var webBottom = view.bottomAnchor

        if let top = cfg.feature("topBar") {
            let bg = top.color("bg") ?? theme
            let fg = top.color("fg") ?? bg.onColor
            let bar = UIView()
            bar.backgroundColor = bg
            bar.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(bar)

            let stack = UIStackView()
            stack.axis = .horizontal
            stack.alignment = .center
            stack.spacing = 0
            stack.translatesAutoresizingMaskIntoConstraints = false
            bar.addSubview(stack)

            if cfg.feature("drawer") != nil {
                stack.addArrangedSubview(barButton("line.3.horizontal", "Menu", #selector(openDrawer), tint: fg))
            }
            if top.flag("back", true) {
                let b = barButton("chevron.left", "Back", #selector(goBack), tint: fg)
                b.isHidden = true
                backButton = b
                stack.addArrangedSubview(b)
            }
            let title = UILabel()
            title.text = cfg.appName
            title.textColor = fg
            title.font = .systemFont(ofSize: 18, weight: .semibold)
            title.lineBreakMode = .byTruncatingTail
            title.setContentHuggingPriority(.defaultLow, for: .horizontal)
            title.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
            titleLabel = title
            let titleWrap = UIView()
            titleWrap.translatesAutoresizingMaskIntoConstraints = false
            title.translatesAutoresizingMaskIntoConstraints = false
            titleWrap.addSubview(title)
            NSLayoutConstraint.activate([
                title.leadingAnchor.constraint(equalTo: titleWrap.leadingAnchor, constant: 12),
                title.trailingAnchor.constraint(equalTo: titleWrap.trailingAnchor, constant: -4),
                title.centerYAnchor.constraint(equalTo: titleWrap.centerYAnchor),
                titleWrap.heightAnchor.constraint(equalToConstant: 44),
            ])
            stack.addArrangedSubview(titleWrap)
            if top.flag("home", false) { stack.addArrangedSubview(barButton("house", "Home", #selector(goHome), tint: fg)) }
            if top.flag("refresh", true) {
                stack.addArrangedSubview(barButton("arrow.clockwise", "Refresh", #selector(refreshTapped), tint: fg))
            }
            addToolButtons(stack, fg: fg, share: top.flag("share", false),
                           others: (cfg.feature("drawer") != nil ? 1 : 0) + (top.flag("back", true) ? 1 : 0)
                               + (top.flag("home", false) ? 1 : 0) + (top.flag("refresh", true) ? 1 : 0))
            NSLayoutConstraint.activate([
                bar.topAnchor.constraint(equalTo: webTop),
                bar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
                bar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
                bar.heightAnchor.constraint(equalToConstant: 52),
                stack.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: 4),
                stack.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -4),
                stack.topAnchor.constraint(equalTo: bar.topAnchor),
                stack.bottomAnchor.constraint(equalTo: bar.bottomAnchor),
            ])
            topBar = bar
            topStack = stack
            topDefaultBg = bg
            topDefaultFg = fg
            webTop = bar.bottomAnchor
        }

        if let nav = cfg.feature("bottomNav") {
            let items = nav.list("items")
            if items.count >= 2 {
                navItems = items
                let chosen = nav.color("bg") ?? .white
                let theme = self.theme
                var bg = chosen
                if cfg.feature("darkMode") != nil && chosen.isLight {
                    // A light tab bar turns dark with the phone's dark mode, like the pages.
                    let dark = UIColor(red: 0.118, green: 0.122, blue: 0.133, alpha: 1)
                    bg = UIColor { $0.userInterfaceStyle == .dark ? dark : chosen }
                }
                navActive = nav.color("active") ?? UIColor { trait in
                    bg.resolvedColor(with: trait).isLight ? theme : .white
                }
                let bar = UIView()
                bar.backgroundColor = bg
                bar.translatesAutoresizingMaskIntoConstraints = false
                let line = UIView()
                line.backgroundColor = UIColor { UIColor(white: bg.resolvedColor(with: $0).isLight ? 0 : 1, alpha: 0.1) }
                line.translatesAutoresizingMaskIntoConstraints = false
                bar.addSubview(line)
                let stack = UIStackView()
                stack.axis = .horizontal
                stack.distribution = .fillEqually
                stack.translatesAutoresizingMaskIntoConstraints = false
                bar.addSubview(stack)
                for (i, item) in items.enumerated() {
                    let b = UIButton(type: .custom)
                    b.tag = i
                    let symbolName = WebViewController.navSymbols[item.str("icon", "home")] ?? "circle.fill"
                    b.setImage(symbol(symbolName, 19), for: .normal)
                    b.setTitle(item.str("label"), for: .normal)
                    b.titleLabel?.font = .systemFont(ofSize: 11, weight: .medium)
                    b.addTarget(self, action: #selector(navTapped(_:)), for: .touchUpInside)
                    stack.addArrangedSubview(b)
                    navButtons.append(b)
                }
                view.addSubview(bar)
                NSLayoutConstraint.activate([
                    bar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
                    bar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
                    bar.bottomAnchor.constraint(equalTo: view.bottomAnchor),
                    line.topAnchor.constraint(equalTo: bar.topAnchor),
                    line.leadingAnchor.constraint(equalTo: bar.leadingAnchor),
                    line.trailingAnchor.constraint(equalTo: bar.trailingAnchor),
                    line.heightAnchor.constraint(equalToConstant: 0.5),
                    stack.topAnchor.constraint(equalTo: bar.topAnchor),
                    stack.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
                    stack.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
                    stack.bottomAnchor.constraint(equalTo: guide.bottomAnchor),
                    stack.heightAnchor.constraint(equalToConstant: 58),
                ])
                navBar = bar
                webBottom = bar.topAnchor
                layoutNavButtons()
                updateNav(for: cfg.homeURL)
            }
        }

        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            webView.bottomAnchor.constraint(equalTo: webBottom),
        ])
        if cfg.feature("noticeBar") != nil {
            // An empty column between the top bar and the page; the notice bar drops into it.
            let holder = UIStackView()
            holder.axis = .vertical
            holder.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(holder)
            let empty = holder.heightAnchor.constraint(equalToConstant: 0)
            empty.priority = .defaultLow
            NSLayoutConstraint.activate([
                holder.topAnchor.constraint(equalTo: webTop),
                holder.leadingAnchor.constraint(equalTo: view.leadingAnchor),
                holder.trailingAnchor.constraint(equalTo: view.trailingAnchor),
                empty,
            ])
            noticeHolder = holder
            webTop = holder.bottomAnchor
        }
        let topConstraint = webView.topAnchor.constraint(equalTo: webTop)
        topConstraint.isActive = true
        webTopConstraint = topConstraint
    }

    /// Icon above the label in each tab.
    private func layoutNavButtons() {
        for b in navButtons {
            guard let image = b.imageView?.image, let title = b.titleLabel?.text else { continue }
            let titleSize = (title as NSString).size(withAttributes: [.font: b.titleLabel?.font ?? UIFont.systemFont(ofSize: 11)])
            let spacing: CGFloat = 4
            b.titleEdgeInsets = UIEdgeInsets(top: 0, left: -image.size.width, bottom: -(image.size.height + spacing), right: 0)
            b.imageEdgeInsets = UIEdgeInsets(top: -(titleSize.height + spacing), left: 0, bottom: 0, right: -titleSize.width)
        }
    }

    func updateNav(for url: URL?) {
        guard !navButtons.isEmpty else { return }
        let bg = navBar?.backgroundColor ?? .white
        let idle = UIColor { bg.resolvedColor(with: $0).isLight ? UIColor(white: 0.45, alpha: 1) : UIColor(white: 1, alpha: 0.6) }
        var best = -1
        var bestLength = -1
        for (i, item) in navItems.enumerated() where linkMatches(url, item.str("url")) {
            let length = URL(string: item.str("url"))?.path.count ?? 0
            if length > bestLength {
                best = i
                bestLength = length
            }
        }
        for (i, b) in navButtons.enumerated() {
            let color = i == best ? navActive : idle
            b.tintColor = color
            b.setTitleColor(color, for: .normal)
        }
    }

    @objc private func navTapped(_ sender: UIButton) {
        guard sender.tag < navItems.count, let url = URL(string: navItems[sender.tag].str("url")) else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        updateNav(for: url)
        webView.load(request(for: url))
    }

    private func updateTitle(_ pageTitle: String?) {
        guard let label = titleLabel else { return }
        if cfg.feature("topBar")?.str("title", "app") == "page", let t = pageTitle, !t.isEmpty {
            label.text = t
        } else {
            label.text = cfg.appName
        }
    }

    private func updateBackButton() {
        guard let b = backButton else { return }
        UIView.animate(withDuration: 0.2) { b.isHidden = !self.webView.canGoBack }
    }

    @objc private func goBack() { if webView.canGoBack { webView.goBack() } }
    @objc private func goHome() { webView.load(request(for: cfg.homeURL)) }
    @objc private func refreshTapped() { webView.reload() }
    @objc func shareTapped() {
        if let url = webView.url { share(items: [url]) }
    }

    // MARK: - Loader

    private func buildLoader() {
        let options = cfg.feature("loader")
        let style = options?.str("style", "bar") ?? "bar"
        let color = options?.color("color") ?? (theme.isLight ? .darkGray : theme)
        if style == "spinner" {
            let s = UIActivityIndicatorView(style: .large)
            s.color = color
            s.hidesWhenStopped = true
            s.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(s)
            NSLayoutConstraint.activate([
                s.centerXAnchor.constraint(equalTo: webView.centerXAnchor),
                s.centerYAnchor.constraint(equalTo: webView.centerYAnchor),
            ])
            spinner = s
            return
        }
        guard style == "bar" else { return }
        progressView.translatesAutoresizingMaskIntoConstraints = false
        progressView.progressTintColor = color
        progressView.trackTintColor = .clear
        progressView.isHidden = true
        view.addSubview(progressView)
        NSLayoutConstraint.activate([
            progressView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            progressView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            progressView.topAnchor.constraint(equalTo: webView.topAnchor),
            progressView.heightAnchor.constraint(equalToConstant: 3),
        ])
        observations.append(webView.observe(\.estimatedProgress, options: [.new]) { [weak self] web, _ in
            guard let self = self else { return }
            self.progressView.setProgress(Float(web.estimatedProgress), animated: true)
            self.progressView.isHidden = web.estimatedProgress >= 1.0
        })
    }

    // MARK: - Offline screen and connection banner

    private func buildOfflineView() {
        offlineView.translatesAutoresizingMaskIntoConstraints = false
        offlineView.backgroundColor = .systemBackground
        offlineView.isHidden = true
        view.addSubview(offlineView)
        NSLayoutConstraint.activate([
            offlineView.leadingAnchor.constraint(equalTo: webView.leadingAnchor),
            offlineView.trailingAnchor.constraint(equalTo: webView.trailingAnchor),
            offlineView.topAnchor.constraint(equalTo: webView.topAnchor),
            offlineView.bottomAnchor.constraint(equalTo: webView.bottomAnchor),
        ])

        let icon = UIImageView(image: symbol("wifi.slash", 44, .regular))
        icon.tintColor = theme.isLight ? .darkGray : theme

        let label = UILabel()
        label.text = cfg.string("offlineMessage", "No internet connection")
        label.textColor = .secondaryLabel
        label.font = .systemFont(ofSize: 17, weight: .medium)
        label.numberOfLines = 0
        label.textAlignment = .center

        let button = UIButton(type: .system)
        button.setTitle(cfg.string("retryLabel", "Retry"), for: .normal)
        button.titleLabel?.font = .boldSystemFont(ofSize: 16)
        button.backgroundColor = theme
        button.setTitleColor(theme.onColor, for: .normal)
        button.layer.cornerRadius = 22
        button.contentEdgeInsets = UIEdgeInsets(top: 0, left: 28, bottom: 0, right: 28)
        button.heightAnchor.constraint(equalToConstant: 44).isActive = true
        button.addTarget(self, action: #selector(retry), for: .touchUpInside)

        let stack = UIStackView(arrangedSubviews: [icon, label, button])
        stack.axis = .vertical
        stack.spacing = 18
        stack.alignment = .center
        stack.translatesAutoresizingMaskIntoConstraints = false
        offlineView.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: offlineView.centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: offlineView.centerYAnchor),
            stack.leadingAnchor.constraint(greaterThanOrEqualTo: offlineView.leadingAnchor, constant: 32),
            stack.trailingAnchor.constraint(lessThanOrEqualTo: offlineView.trailingAnchor, constant: -32),
        ])
    }

    @objc private func retry() {
        offlineView.isHidden = true
        webView.load(request(for: failedURL ?? cfg.homeURL))
    }

    func showOffline(_ error: Error, url: URL?) {
        let ns = error as NSError
        // -999 is a cancelled load (for example a redirect); 102 is a load we interrupted ourselves.
        if ns.code == NSURLErrorCancelled { return }
        if ns.domain == "WebKitErrorDomain" && (ns.code == 102 || ns.code == 204) { return }
        failedURL = (ns.userInfo[NSURLErrorFailingURLErrorKey] as? URL) ?? url ?? failedURL
        offlineView.isHidden = false
        webView.scrollView.refreshControl?.endRefreshing()
        pageReady()
    }

    private func startNetworkBanner() {
        let banner = cfg.feature("netBanner")
        guard banner != nil || cfg.feature("offlineCache") != nil else { return }
        let options = banner ?? [:]
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.setOffline(!online)
                guard banner != nil else { return }
                if !online {
                    self.wasOffline = true
                    self.showBanner(options.str("offline", "You are offline"), color: UIColor(red: 0.75, green: 0.16, blue: 0.13, alpha: 1), stay: true)
                } else if self.wasOffline {
                    self.wasOffline = false
                    self.showBanner(options.str("online", "Back online"), color: UIColor(red: 0.12, green: 0.55, blue: 0.27, alpha: 1), stay: false)
                }
            }
        }
        monitor.start(queue: DispatchQueue(label: "w2a.network"))
        pathMonitor = monitor
    }

    private var banner: UILabel?

    private func showBanner(_ text: String, color: UIColor, stay: Bool) {
        banner?.removeFromSuperview()
        let label = PaddedLabel()
        label.text = text
        label.textColor = .white
        label.font = .systemFont(ofSize: 14, weight: .semibold)
        label.textAlignment = .center
        label.backgroundColor = color
        label.translatesAutoresizingMaskIntoConstraints = false
        view.insertSubview(label, belowSubview: lockView ?? splashView ?? offlineView)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            label.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            label.topAnchor.constraint(equalTo: webView.topAnchor),
            label.heightAnchor.constraint(equalToConstant: 34),
        ])
        label.transform = CGAffineTransform(translationX: 0, y: -34)
        UIView.animate(withDuration: 0.25) { label.transform = .identity }
        banner = label
        if !stay {
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) { [weak label] in
                UIView.animate(withDuration: 0.25, animations: { label?.alpha = 0 }) { _ in label?.removeFromSuperview() }
            }
        }
    }

    /// Small message at the bottom of the screen.
    func showSnack(_ text: String) {
        guard !text.isEmpty else { return }
        snack?.removeFromSuperview()
        let label = PaddedLabel()
        label.text = text
        label.textColor = .white
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.numberOfLines = 3
        label.textAlignment = .center
        label.backgroundColor = UIColor(white: 0.13, alpha: 0.94)
        label.layer.cornerRadius = 12
        label.clipsToBounds = true
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 24),
            label.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -24),
            label.bottomAnchor.constraint(equalTo: webView.bottomAnchor, constant: -24),
        ])
        label.alpha = 0
        label.transform = CGAffineTransform(translationX: 0, y: 12)
        UIView.animate(withDuration: 0.22) {
            label.alpha = 1
            label.transform = .identity
        }
        snack = label
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.4) { [weak label] in
            UIView.animate(withDuration: 0.22, animations: { label?.alpha = 0 }) { _ in label?.removeFromSuperview() }
        }
    }

    // MARK: - Floating button

    private func buildFab() {
        guard let fab = cfg.feature("fab") else { return }
        let color = fab.color("color") ?? theme
        let b = UIButton(type: .custom)
        b.backgroundColor = color
        b.tintColor = color.onColor
        b.setImage(symbol(WebViewController.navSymbols[fab.str("icon", "chat")] ?? "bubble.left.fill", 22), for: .normal)
        b.layer.cornerRadius = 28
        b.layer.shadowColor = UIColor.black.cgColor
        b.layer.shadowOpacity = 0.25
        b.layer.shadowRadius = 8
        b.layer.shadowOffset = CGSize(width: 0, height: 4)
        b.accessibilityLabel = ["whatsapp": "Chat on WhatsApp", "tel": "Call us", "email": "Email us", "share": "Share"][fab.str("action", "url")] ?? "Open link"
        b.translatesAutoresizingMaskIntoConstraints = false
        b.addTarget(self, action: #selector(fabTapped), for: .touchUpInside)
        view.addSubview(b)
        let left = fab.str("position", "right") == "left"
        NSLayoutConstraint.activate([
            b.widthAnchor.constraint(equalToConstant: 56),
            b.heightAnchor.constraint(equalToConstant: 56),
            b.bottomAnchor.constraint(equalTo: navBar == nil ? view.safeAreaLayoutGuide.bottomAnchor : webView.bottomAnchor,
                                      constant: -18),
            left ? b.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 18)
                 : b.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -18),
        ])
    }

    @objc private func fabTapped() {
        guard let fab = cfg.feature("fab") else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        let value = fab.str("value").trimmingCharacters(in: .whitespaces)
        let digits = value.filter { $0.isNumber || $0 == "+" }
        switch fab.str("action", "whatsapp") {
        case "whatsapp":
            if let url = URL(string: "https://wa.me/" + digits.replacingOccurrences(of: "+", with: "")) {
                UIApplication.shared.open(url)
            }
        case "tel":
            if let url = URL(string: "tel:" + digits) { UIApplication.shared.open(url) }
        case "email":
            if let url = URL(string: "mailto:" + value) { UIApplication.shared.open(url) }
        case "url":
            if let url = URL(string: value) { openLink(url) }
        default:
            if let url = webView.url { share(items: [url]) }
        }
    }

    // MARK: - Splash screen

    private func buildSplash() {
        guard let options = cfg.feature("splash") else { return }
        let bg = cfg.color("splashColor", "#FFFFFF")
        let overlay = UIView()
        overlay.backgroundColor = bg
        overlay.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(overlay)
        NSLayoutConstraint.activate([
            overlay.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlay.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlay.topAnchor.constraint(equalTo: view.topAnchor),
            overlay.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        // Optional full-screen image or short video behind the logo.
        let fill: UIView.ContentMode = options.str("fit", "cover") == "contain" ? .scaleAspectFit : .scaleAspectFill
        switch options.str("background", "color") {
        case "image":
            let url = ["png", "jpg", "webp"].lazy.compactMap { Bundle.main.url(forResource: "splash_bg", withExtension: $0) }.first
            if let url, let data = try? Data(contentsOf: url), let picture = UIImage(data: data) {
                let art = UIImageView(image: picture)
                art.contentMode = fill
                art.clipsToBounds = true
                pin(art, in: overlay)
            }
        case "video":
            if let url = Bundle.main.url(forResource: "splash", withExtension: "mp4") {
                let player = AVPlayer(url: url)
                player.isMuted = true
                let holder = PlayerView()
                holder.playerLayer.player = player
                holder.playerLayer.videoGravity = fill == .scaleAspectFit ? .resizeAspect : .resizeAspectFill
                pin(holder, in: overlay)
                splashVideoPlaying = true
                splashVideoEnd = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime,
                                                                         object: player.currentItem, queue: .main) { [weak self] _ in
                    self?.splashVideoEnded()
                }
                player.play()
            }
        default:
            break
        }
        let style = options.str("style", "logo_name")
        let stack = UIStackView()
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false
        overlay.addSubview(stack)
        if style == "logo" || style == "logo_name", let logo = UIImage(named: "SplashLogo") {
            let image = UIImageView(image: logo)
            image.contentMode = .scaleAspectFill
            image.clipsToBounds = true
            image.layer.cornerRadius = 26
            image.translatesAutoresizingMaskIntoConstraints = false
            image.widthAnchor.constraint(equalToConstant: 112).isActive = true
            image.heightAnchor.constraint(equalToConstant: 112).isActive = true
            stack.addArrangedSubview(image)
            animateSplashLogo(image, options.str("animation", "pop"))
        }
        if style == "name" || style == "logo_name" {
            let name = UILabel()
            name.text = cfg.appName
            name.font = .systemFont(ofSize: 24, weight: .bold)
            name.textColor = options.color("textColor") ?? bg.onColor
            stack.addArrangedSubview(name)
            if options.str("animation", "pop") != "none" {
                name.alpha = 0
                name.transform = CGAffineTransform(translationX: 0, y: 10)
                UIView.animate(withDuration: 0.46, delay: 0.22, options: .curveEaseOut) {
                    name.alpha = 1
                    name.transform = .identity
                }
            }
        }
        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: overlay.centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: overlay.centerYAnchor, constant: -20),
        ])
        overlay.accessibilityViewIsModal = true
        overlay.accessibilityIdentifier = "Splash"
        splashView = overlay
        splashStarted = Date()
        let maxMs = max(1000, options.int("maxMs", 4000))
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(maxMs)) { [weak self] in self?.hideSplash() }
    }

    private func pin(_ child: UIView, in parent: UIView) {
        child.translatesAutoresizingMaskIntoConstraints = false
        parent.addSubview(child)
        NSLayoutConstraint.activate([
            child.leadingAnchor.constraint(equalTo: parent.leadingAnchor),
            child.trailingAnchor.constraint(equalTo: parent.trailingAnchor),
            child.topAnchor.constraint(equalTo: parent.topAnchor),
            child.bottomAnchor.constraint(equalTo: parent.bottomAnchor),
        ])
    }

    /// The splash waits for its video to end, then for the page (whichever comes last).
    private func splashVideoEnded() {
        splashVideoPlaying = false
        if let token = splashVideoEnd { NotificationCenter.default.removeObserver(token) }
        splashVideoEnd = nil
        if firstPageDone { hideSplashSoon() }
    }

    /// Called when the first page finished (or failed).
    private func pageReady() {
        guard !firstPageDone else { return }
        firstPageDone = true
        hideSplashSoon()
    }

    private func hideSplashSoon() {
        guard splashView != nil, !splashVideoPlaying, let options = cfg.feature("splash") else { return }
        let minMs = Double(max(0, options.int("minMs", 600)))
        let elapsed = Date().timeIntervalSince(splashStarted) * 1000
        DispatchQueue.main.asyncAfter(deadline: .now() + max(0, minMs - elapsed) / 1000) { [weak self] in
            self?.hideSplash()
        }
    }

    private func hideSplash() {
        guard let overlay = splashView else { return }
        splashView = nil
        splashVideoPlaying = false
        if let token = splashVideoEnd { NotificationCenter.default.removeObserver(token) }
        splashVideoEnd = nil
        for case let holder as PlayerView in overlay.subviews { holder.playerLayer.player?.pause() }
        setNeedsStatusBarAppearanceUpdate()
        UIView.animate(withDuration: 0.3, animations: {
            overlay.alpha = 0
            overlay.transform = CGAffineTransform(scaleX: 1.04, y: 1.04)
        }) { _ in overlay.removeFromSuperview() }
        afterSplash()
    }

    /// Once the splash is gone: the lock first, then the welcome slides, then permission and other prompts,
    /// so a system prompt never covers the lock or the slides.
    private func afterSplash() {
        guard !startupDone else { return }
        startupDone = true
        if lockView != nil { unlock() } else { continueStartup() }
    }

    /// The age check or privacy screen was answered: the next one, or the rest of the start.
    func gateClosed() {
        buildGate()
        if let gate = gateView, let lock = lockView { view.insertSubview(gate, belowSubview: lock) }
        if gateView == nil { continueStartup() }
    }

    var startupContinued = false

    func continueStartup() {
        guard !startupContinued, lockView == nil, gateView == nil, onboardingView == nil, splashView == nil else { return }
        startupContinued = true
        requestStartupPermissions()
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { [weak self] in self?.maybeShowAnnouncement() }
        scheduleReminder()
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { [weak self] in
            self?.maybeAskRating()
            self?.checkForUpdate()
        }
    }

    // MARK: - Welcome slides

    private func buildOnboarding() {
        guard let options = cfg.feature("onboarding") else { return }
        let slides = options.list("slides")
        let defaults = UserDefaults.standard
        guard !slides.isEmpty, !defaults.bool(forKey: "w2a.onboarded") else { return }
        let bg = options.color("bg") ?? theme
        let fg = bg.onColor

        let overlay = UIView()
        overlay.backgroundColor = bg
        overlay.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(overlay)
        NSLayoutConstraint.activate([
            overlay.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlay.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlay.topAnchor.constraint(equalTo: view.topAnchor),
            overlay.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        let guide = overlay.safeAreaLayoutGuide

        let scroll = UIScrollView()
        scroll.isPagingEnabled = true
        scroll.showsHorizontalScrollIndicator = false
        scroll.delegate = self
        scroll.translatesAutoresizingMaskIntoConstraints = false
        overlay.addSubview(scroll)
        let row = UIStackView()
        row.axis = .horizontal
        row.distribution = .fillEqually
        row.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(row)

        for slide in slides {
            let page = UIView()
            let icon = UIImageView(image: UIImage(named: "SplashLogo") ?? symbol("app.fill", 56, .regular))
            icon.tintColor = fg
            icon.contentMode = .scaleAspectFill
            icon.clipsToBounds = true
            icon.layer.cornerRadius = 30
            icon.translatesAutoresizingMaskIntoConstraints = false
            let title = UILabel()
            title.text = slide.str("title")
            title.font = .systemFont(ofSize: 27, weight: .bold)
            title.textColor = fg
            title.numberOfLines = 0
            title.textAlignment = .center
            let text = UILabel()
            text.text = slide.str("text")
            text.font = .systemFont(ofSize: 17)
            text.textColor = fg.withAlphaComponent(0.85)
            text.numberOfLines = 0
            text.textAlignment = .center
            let stack = UIStackView(arrangedSubviews: [icon, title, text])
            stack.axis = .vertical
            stack.alignment = .center
            stack.spacing = 18
            stack.setCustomSpacing(32, after: icon)
            stack.translatesAutoresizingMaskIntoConstraints = false
            page.addSubview(stack)
            NSLayoutConstraint.activate([
                icon.widthAnchor.constraint(equalToConstant: 120),
                icon.heightAnchor.constraint(equalToConstant: 120),
                stack.centerYAnchor.constraint(equalTo: page.centerYAnchor, constant: -30),
                stack.leadingAnchor.constraint(equalTo: page.leadingAnchor, constant: 32),
                stack.trailingAnchor.constraint(equalTo: page.trailingAnchor, constant: -32),
            ])
            row.addArrangedSubview(page)
        }

        let dots = UIPageControl()
        dots.numberOfPages = slides.count
        dots.currentPageIndicatorTintColor = fg
        dots.pageIndicatorTintColor = fg.withAlphaComponent(0.35)
        dots.isUserInteractionEnabled = false
        dots.hidesForSinglePage = true
        dots.translatesAutoresizingMaskIntoConstraints = false
        overlay.addSubview(dots)

        let button = UIButton(type: .system)
        button.backgroundColor = fg
        button.setTitleColor(bg, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        button.layer.cornerRadius = 27
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(onboardingNext), for: .touchUpInside)
        overlay.addSubview(button)

        let skip = UIButton(type: .system)
        skip.setTitle("Skip", for: .normal)
        skip.setTitleColor(fg.withAlphaComponent(0.85), for: .normal)
        skip.titleLabel?.font = .systemFont(ofSize: 16, weight: .medium)
        skip.translatesAutoresizingMaskIntoConstraints = false
        skip.addTarget(self, action: #selector(finishOnboarding), for: .touchUpInside)
        skip.isHidden = slides.count < 2
        overlay.addSubview(skip)

        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: guide.topAnchor),
            scroll.leadingAnchor.constraint(equalTo: overlay.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: overlay.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: dots.topAnchor, constant: -8),
            row.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            row.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            row.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            row.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            row.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
            row.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, multiplier: CGFloat(slides.count)),
            dots.centerXAnchor.constraint(equalTo: overlay.centerXAnchor),
            dots.bottomAnchor.constraint(equalTo: button.topAnchor, constant: -16),
            button.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: 28),
            button.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -28),
            button.bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -20),
            button.heightAnchor.constraint(equalToConstant: 54),
            skip.topAnchor.constraint(equalTo: guide.topAnchor, constant: 8),
            skip.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -20),
        ])
        overlay.accessibilityViewIsModal = true
        onboardingView = overlay
        onboardingScroll = scroll
        onboardingDots = dots
        onboardingButton = button
        updateOnboardingButton()
    }

    private var onboardingPage: Int {
        guard let s = onboardingScroll, s.bounds.width > 0 else { return 0 }
        return Int(round(s.contentOffset.x / s.bounds.width))
    }

    private func updateOnboardingButton() {
        guard let dots = onboardingDots, let button = onboardingButton else { return }
        dots.currentPage = onboardingPage
        let last = onboardingPage >= dots.numberOfPages - 1
        let label = last ? (cfg.feature("onboarding")?.str("button", "Get started") ?? "Get started") : "Next"
        button.setTitle(label, for: .normal)
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        if scrollView === onboardingScroll { updateOnboardingButton() }
    }

    @objc private func onboardingNext() {
        guard let s = onboardingScroll, let dots = onboardingDots else { return }
        if onboardingPage >= dots.numberOfPages - 1 {
            finishOnboarding()
        } else {
            s.setContentOffset(CGPoint(x: CGFloat(onboardingPage + 1) * s.bounds.width, y: 0), animated: true)
        }
    }

    @objc private func finishOnboarding() {
        UserDefaults.standard.set(true, forKey: "w2a.onboarded")
        guard let overlay = onboardingView else { return }
        onboardingView = nil
        UIView.animate(withDuration: 0.3, animations: { overlay.alpha = 0 }) { _ in overlay.removeFromSuperview() }
        continueStartup()
    }

    // MARK: - App lock

    private func buildLock() {
        guard cfg.feature("appLock") != nil else { return }
        let built = makeLockScreen()
        view.addSubview(built.view)
        NSLayoutConstraint.activate([
            built.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            built.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            built.view.topAnchor.constraint(equalTo: view.topAnchor),
            built.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        built.fingerprint.addTarget(self, action: #selector(unlock), for: .touchUpInside)
        built.button.addTarget(self, action: #selector(unlock), for: .touchUpInside)
        lockView = built.view
        lockButton = built.button
    }

    @objc private func unlock() {
        guard lockView != nil, !unlocking else { return }
        // Face ID can only be shown while the app is in front; try again as soon as it is.
        guard UIApplication.shared.applicationState == .active else {
            unlockWhenActive = true
            return
        }
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            // No passcode on this phone: nothing to unlock with.
            removeLock()
            return
        }
        unlocking = true
        systemPromptExpected = true
        let reason = cfg.feature("appLock")?.str("title", "Unlock") ?? "Unlock"
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { [weak self] ok, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.unlocking = false
                if ok {
                    self.removeLock()
                    return
                }
                let code = (error as? LAError)?.code
                self.lockButton?.accessibilityValue = "error \((error as NSError?)?.code ?? 0)"
                // Interrupted by the system (another prompt, the app moving to the back): ask again once it is in front.
                if code == .systemCancel || code == .appCancel || code == .notInteractive {
                    if UIApplication.shared.applicationState == .active {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { self.unlock() }
                    } else {
                        self.unlockWhenActive = true
                    }
                }
            }
        }
    }

    private func removeLock() {
        guard let overlay = lockView else { return }
        lockView = nil
        UIView.animate(withDuration: 0.25, animations: { overlay.alpha = 0 }) { _ in overlay.removeFromSuperview() }
        continueStartup()
    }

    @objc private func didEnterBackground() {
        leftAt = Date()
        featuresEnteredBackground()
        if let url = webView.url, cfg.feature("rememberPage") != nil, isInternal(url) {
            UserDefaults.standard.set(url.absoluteString, forKey: "w2a.lastPage")
        }
    }

    @objc private func didBecomeActive() {
        featuresBecameActive()
        guard unlockWhenActive else { return }
        unlockWhenActive = false
        if lockView != nil, splashView == nil { unlock() }
    }

    @objc private func willEnterForeground() {
        guard let lock = cfg.feature("appLock"), lockView == nil, let left = leftAt else { return }
        let minutes = Int(lock.str("relock", "1")) ?? 1
        if minutes < 0 { return }
        if Date().timeIntervalSince(left) >= Double(minutes * 60) {
            buildLock()
            if let overlay = lockView { view.bringSubviewToFront(overlay) }
            unlock()
        }
    }

    // MARK: - Reminder, rating and update prompts

    private func scheduleReminder() {
        let center = UNUserNotificationCenter.current()
        guard let options = cfg.feature("reminder") else {
            center.removePendingNotificationRequests(withIdentifiers: ["w2a.reminder"])
            return
        }
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            let parts = options.str("time", "19:00").split(separator: ":").compactMap { Int($0) }
            var when = DateComponents()
            when.hour = parts.count > 0 ? parts[0] : 19
            when.minute = parts.count > 1 ? parts[1] : 0
            let content = UNMutableNotificationContent()
            content.title = options.str("title", "We miss you")
            content.body = options.str("text", "Open the app to see what is new.")
            content.sound = .default
            let trigger = UNCalendarNotificationTrigger(dateMatching: when, repeats: true)
            center.add(UNNotificationRequest(identifier: "w2a.reminder", content: content, trigger: trigger))
        }
    }

    private func maybeAskRating() {
        guard let options = cfg.feature("rateApp") else { return }
        let defaults = UserDefaults.standard
        if defaults.bool(forKey: "w2a.rated") { return }
        let count = defaults.integer(forKey: "w2a.launches") + 1
        defaults.set(count, forKey: "w2a.launches")
        // Asked after N launches, then again after 2N and 3N more, never more than 3 times.
        let asked = defaults.integer(forKey: "w2a.rateAsks")
        guard asked < 3, count >= max(2, options.int("launches", 5)) * (asked + 1),
              presentedViewController == nil, let scene = view.window?.windowScene else { return }
        defaults.set(asked + 1, forKey: "w2a.rateAsks")
        defaults.set(0, forKey: "w2a.launches")
        // Apple asks apps to use its own rating window (App Store guideline 5.6.1), so iOS shows that one.
        SKStoreReviewController.requestReview(in: scene)
    }

    private func checkForUpdate() {
        guard let options = cfg.feature("updatePrompt"), let url = URL(string: options.str("checkUrl")) else { return }
        var req = URLRequest(url: url)
        req.cachePolicy = .reloadIgnoringLocalCacheData
        req.timeoutInterval = 12
        URLSession.shared.dataTask(with: req) { [weak self] data, _, _ in
            guard let data = data,
                  let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return }
            let latest = json.int("versionCode", 0)
            let current = Int(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0") ?? 0
            guard latest > current else { return }
            DispatchQueue.main.async { self?.showUpdate(json, options) }
        }.resume()
    }

    private func showUpdate(_ json: [String: Any], _ options: [String: Any]) {
        guard presentedViewController == nil else { return }
        let force = (json["force"] as? Bool) ?? options.flag("force", false)
        let alert = UIAlertController(title: "Update available",
                                      message: json.str("message", options.str("message", "A new version is available.")),
                                      preferredStyle: .alert)
        if !force { alert.addAction(UIAlertAction(title: "Later", style: .cancel)) }
        alert.addAction(UIAlertAction(title: "Update", style: .default) { [weak self] _ in
            if let target = URL(string: json.str("iosUrl", json.str("url"))) { UIApplication.shared.open(target) }
            if force { DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { self?.showUpdate(json, options) } }
        })
        present(alert, animated: true)
    }

    // MARK: - Navigation

    func isInternal(_ url: URL) -> Bool {
        guard let rawHost = url.host?.lowercased() else { return true }
        let host = AppConfig.stripWww(rawHost)
        for h in cfg.internalHosts where host == h || host.hasSuffix("." + h) {
            return true
        }
        return false
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.allow)
            return
        }
        let scheme = (url.scheme ?? "").lowercased()

        if scheme == AppConfig.localScheme {
            decisionHandler(.allow)
            return
        }

        if scheme == "http" || scheme == "https" {
            let mainFrame = navigationAction.targetFrame?.isMainFrame ?? true
            if let stay = cfg.feature("stayInApp"), mainFrame, !isInternal(url) {
                showSnack(stay.str("message", "This link cannot be opened here."))
                decisionHandler(.cancel)
                return
            }
            if cfg.bool("externalLinksInBrowser", true), mainFrame,
               navigationAction.navigationType == .linkActivated, !isInternal(url) {
                openOutside(url)
                decisionHandler(.cancel)
                return
            }
            if mainFrame, navigationAction.targetFrame != nil, useSavedCopy(navigationAction) {
                decisionHandler(.cancel)
                return
            }
            if navigationAction.targetFrame == nil {
                // target="_blank": keep it inside the app.
                webView.load(navigationAction.request)
                decisionHandler(.cancel)
                return
            }
            decisionHandler(.allow)
            return
        }

        if ["about", "data", "blob", "javascript", "file"].contains(scheme) {
            decisionHandler(.allow)
            return
        }

        // The app's own link scheme opens the matching page.
        if let links = cfg.feature("deepLinks"), scheme == links.str("scheme").lowercased(), !scheme.isEmpty,
           let page = WebViewController.pageURL(forDeepLink: url) {
            webView.load(request(for: page))
            decisionHandler(.cancel)
            return
        }

        // tel:, mailto:, sms:, whatsapp:, itms-apps: ...
        UIApplication.shared.open(url)
        decisionHandler(.cancel)
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse,
                 decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        noteHttpStatus(navigationResponse)
        if !navigationResponse.canShowMIMEType, cfg.bool("downloads", true),
           let url = navigationResponse.response.url {
            // Files the web view cannot display are handed to Safari to download.
            UIApplication.shared.open(url)
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        spinner?.startAnimating()
        errorPageNavigationStarted(webView.url)
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        updateNav(for: webView.url)
        pageCommitted(webView.url)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        offlineView.isHidden = true
        errorPageNavigationFinished(webView.url)
        webView.scrollView.refreshControl?.endRefreshing()
        spinner?.stopAnimating()
        updateNav(for: webView.url)
        updateBackButton()
        pageReady()
        pageFinished()
        if let url = webView.url, cfg.feature("rememberPage") != nil, isInternal(url) {
            UserDefaults.standard.set(url.absoluteString, forKey: "w2a.lastPage")
        }
        if ProcessInfo.processInfo.arguments.contains("-w2aSelfTest") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) { [weak self] in self?.writeSelfTest() }
        }
    }

    /// Automated tests only (started with -w2aSelfTest): writes what the app shows to Documents/selftest.json.
    private func writeSelfTest() {
        webView.evaluateJavaScript("document.getElementById('status')?document.getElementById('status').textContent:''") {
            [weak self] status, _ in
            guard let self = self else { return }
            UNUserNotificationCenter.current().getPendingNotificationRequests { pending in
                DispatchQueue.main.async {
                    let reminders = pending.map { req -> String in
                        let t = req.trigger as? UNCalendarNotificationTrigger
                        return "\(req.identifier) \(req.content.title) \(t?.dateComponents.hour ?? -1):\(t?.dateComponents.minute ?? -1)"
                    }
                    let active = self.navButtons.firstIndex { $0.titleColor(for: .normal) == self.navActive } ?? -1
                    let info: [String: Any] = [
                        "url": self.webView.url?.absoluteString ?? "",
                        "status": status as? String ?? "",
                        "title": self.titleLabel?.text ?? "",
                        "tabs": self.navButtons.map { $0.title(for: .normal) ?? "" },
                        "activeTab": active,
                        "backVisible": !(self.backButton?.isHidden ?? true),
                        "splash": self.splashView != nil,
                        "lock": self.lockView != nil,
                        "onboarding": self.onboardingView != nil,
                        "banner": self.banner?.text ?? "",
                        "features": self.selfTestFeatures(),
                        "reminders": reminders,
                    ]
                    let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                    if let data = try? JSONSerialization.data(withJSONObject: info, options: [.prettyPrinted, .sortedKeys]) {
                        try? data.write(to: dir.appendingPathComponent("selftest.json"))
                    }
                }
            }
        }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!,
                 withError error: Error) {
        spinner?.stopAnimating()
        // The site cannot be reached: show the saved copy if there is one.
        if retryFromCache(error) { return }
        showOffline(error, url: webView.url)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        spinner?.stopAnimating()
        webView.scrollView.refreshControl?.endRefreshing()
        pageReady()
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        webView.reload()
    }

    // MARK: - UI delegate

    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction,
                 windowFeatures: WKWindowFeatures) -> WKWebView? {
        if navigationAction.targetFrame == nil, let url = navigationAction.request.url {
            if cfg.bool("externalLinksInBrowser", true), !isInternal(url) {
                openOutside(url)
            } else {
                webView.load(navigationAction.request)
            }
        }
        return nil
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
        present(alert, animated: true)
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { _ in completionHandler(false) })
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler(true) })
        present(alert, animated: true)
    }

    func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String,
                 defaultText: String?, initiatedByFrame frame: WKFrameInfo,
                 completionHandler: @escaping (String?) -> Void) {
        let alert = UIAlertController(title: nil, message: prompt, preferredStyle: .alert)
        alert.addTextField { $0.text = defaultText }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { _ in completionHandler(nil) })
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in
            completionHandler(alert.textFields?.first?.text)
        })
        present(alert, animated: true)
    }

    func webView(_ webView: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin,
                 initiatedByFrame frame: WKFrameInfo, type: WKMediaCaptureType,
                 decisionHandler: @escaping (WKPermissionDecision) -> Void) {
        let allowed: Bool
        switch type {
        case .camera: allowed = cfg.has("camera")
        case .microphone: allowed = cfg.has("microphone")
        case .cameraAndMicrophone: allowed = cfg.has("camera") && cfg.has("microphone")
        @unknown default: allowed = false
        }
        // iOS still shows its own system prompt the first time.
        decisionHandler(allowed ? .grant : .deny)
    }

    // MARK: - Permissions asked when the app first opens

    private func requestStartupPermissions() {
        guard cfg.bool("askPermissionsOnStart", true) else { return }
        let defaults = UserDefaults.standard
        if defaults.bool(forKey: "web2app.asked") { return }
        defaults.set(true, forKey: "web2app.asked")

        systemPromptExpected = true
        if cfg.has("notifications") {
            UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in }
        }
        if cfg.has("camera") {
            AVCaptureDevice.requestAccess(for: .video) { _ in }
        }
        if cfg.has("microphone") {
            AVCaptureDevice.requestAccess(for: .audio) { _ in }
        }
        if cfg.has("location") {
            locationManager.requestWhenInUseAuthorization()
        }
        if cfg.has("contacts") {
            CNContactStore().requestAccess(for: .contacts) { _, _ in }
        }
        if cfg.has("photos") {
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { _ in }
        }
        if cfg.has("tracking") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                ATTrackingManager.requestTrackingAuthorization { _ in }
            }
        }
    }
}

/// A label with room around its text.
final class PaddedLabel: UILabel {
    var insets = UIEdgeInsets(top: 8, left: 14, bottom: 8, right: 14)

    override func drawText(in rect: CGRect) {
        super.drawText(in: rect.inset(by: insets))
    }

    override var intrinsicContentSize: CGSize {
        let s = super.intrinsicContentSize
        return CGSize(width: s.width + insets.left + insets.right, height: s.height + insets.top + insets.bottom)
    }
}


/// A view whose layer plays video, so it resizes with Auto Layout.
final class PlayerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}
