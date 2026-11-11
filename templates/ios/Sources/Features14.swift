import UIKit
import WebKit
import AVFoundation
import LocalAuthentication
import Network

// Features added in 1.4: page script, files inside the app, friendly error page, read aloud,
// data saver, reload after a long break, the themed lock screen and the splash logo animation.

/// Serves the pages bundled in the app (from an uploaded HTML file or ZIP) like a small web server.
final class LocalFiles: NSObject, WKURLSchemeHandler {
    private static let types: [String: String] = [
        "html": "text/html", "htm": "text/html", "css": "text/css", "js": "text/javascript", "mjs": "text/javascript",
        "json": "application/json", "svg": "image/svg+xml", "png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg",
        "gif": "image/gif", "webp": "image/webp", "avif": "image/avif", "ico": "image/x-icon", "woff": "font/woff",
        "woff2": "font/woff2", "ttf": "font/ttf", "otf": "font/otf", "mp4": "video/mp4", "webm": "video/webm",
        "mp3": "audio/mpeg", "wav": "audio/wav", "ogg": "audio/ogg", "txt": "text/plain", "xml": "application/xml",
        "pdf": "application/pdf", "wasm": "application/wasm", "webmanifest": "application/manifest+json",
    ]

    private lazy var root: URL? = Bundle.main.url(forResource: "www", withExtension: nil)

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let url = urlSchemeTask.request.url else { return }
        var path = url.path.removingPercentEncoding ?? url.path
        if path.isEmpty || path.hasSuffix("/") { path += "index.html" }
        while path.hasPrefix("/") { path.removeFirst() }
        var found: (URL, Data)?
        if let root = root, !path.contains("..") {
            for candidate in [path, path + "/index.html", path + ".html"] {
                let file = root.appendingPathComponent(candidate)
                var isDir: ObjCBool = false
                if FileManager.default.fileExists(atPath: file.path, isDirectory: &isDir), !isDir.boolValue,
                   let data = try? Data(contentsOf: file) {
                    found = (file, data)
                    break
                }
            }
        }
        let status = found == nil ? 404 : 200
        let data = found?.1 ?? Data("<h1 style=\"font-family:sans-serif\">Page not found</h1>".utf8)
        var type = "text/html"
        if let file = found?.0 { type = LocalFiles.types[file.pathExtension.lowercased()] ?? "application/octet-stream" }
        let text = type.hasPrefix("text/") || type.hasSuffix("json") || type.hasSuffix("xml") || type.hasSuffix("javascript")
        let headers = [
            "Content-Type": text ? "\(type); charset=utf-8" : type,
            "Content-Length": String(data.count),
            "Cache-Control": "no-cache",
            "Access-Control-Allow-Origin": "*",
        ]
        let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers)!
        urlSchemeTask.didReceive(response)
        urlSchemeTask.didReceive(data)
        urlSchemeTask.didFinish()
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {}
}

/// A view whose layer is a top-to-bottom gradient.
final class GradientView: UIView {
    override class var layerClass: AnyClass { CAGradientLayer.self }

    func setColors(_ top: UIColor, _ bottom: UIColor) {
        let layer = self.layer as! CAGradientLayer
        layer.colors = [top.cgColor, bottom.cgColor]
        layer.startPoint = CGPoint(x: 0.5, y: 0)
        layer.endPoint = CGPoint(x: 0.5, y: 1)
    }
}

extension UIColor {
    /// Mixes two colours: amount 0 = self, 1 = other.
    func mixed(with other: UIColor, _ amount: CGFloat) -> UIColor {
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        other.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let k = 1 - amount
        return UIColor(red: r1 * k + r2 * amount, green: g1 * k + g2 * amount, blue: b1 * k + b2 * amount, alpha: 1)
    }
}

extension WebViewController: AVSpeechSynthesizerDelegate {

    // MARK: - Page script and read aloud

    func addPageFeatures(_ ucc: WKUserContentController) {
        let page = cfg.string("pageJs")
        if !page.isEmpty {
            ucc.addUserScript(WKUserScript(source: page, injectionTime: .atDocumentEnd, forMainFrameOnly: true))
        }
        if cfg.feature("speech") != nil {
            ucc.add(self, name: "w2aSpeech")
            ucc.addUserScript(WKUserScript(source: """
            (function(){if(window.Web2AppSpeech)return;var h=window.webkit.messageHandlers.w2aSpeech;window.__w2aSpeaking=false;
            window.Web2AppSpeech={speak:function(t,l){h.postMessage({a:'speak',text:t==null?'':String(t),lang:l==null?'':String(l)});},
            stop:function(){h.postMessage({a:'stop'});},isSpeaking:function(){return !!window.__w2aSpeaking;}};})();
            """, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        }
    }

    /// window.Web2AppSpeech: only pages of the app's own site may use the voice.
    func handleSpeech(_ message: WKScriptMessage) {
        guard let options = cfg.feature("speech"), let page = message.frameInfo.request.url, isInternal(page),
              let body = message.body as? [String: Any] else { return }
        if body.str("a") == "stop" {
            speaker?.stopSpeaking(at: .immediate)
            return
        }
        let text = String(body.str("text").trimmingCharacters(in: .whitespacesAndNewlines).prefix(3900))
        guard !text.isEmpty else { return }
        if speaker == nil {
            speaker = AVSpeechSynthesizer()
            speaker?.delegate = self
        }
        if cfg.feature("backgroundAudio") == nil {
            try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        }
        try? AVAudioSession.sharedInstance().setActive(true)
        let utterance = AVSpeechUtterance(string: text)
        let lang = body.str("lang").trimmingCharacters(in: .whitespaces)
        if !lang.isEmpty, let voice = AVSpeechSynthesisVoice(language: lang) { utterance.voice = voice }
        let rate = Float(max(50, min(200, options.int("rate", 100)))) / 100
        utterance.rate = max(AVSpeechUtteranceMinimumSpeechRate,
                             min(AVSpeechUtteranceMaximumSpeechRate, AVSpeechUtteranceDefaultSpeechRate * rate))
        speaker?.stopSpeaking(at: .immediate)
        speaker?.speak(utterance)
    }

    private func setSpeaking(_ on: Bool) {
        webView?.evaluateJavaScript("window.__w2aSpeaking=\(on);", completionHandler: nil)
        if !on, cfg.feature("backgroundAudio") == nil {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) { setSpeaking(true) }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        if !synthesizer.isSpeaking { setSpeaking(false) }
    }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        if !synthesizer.isSpeaking { setSpeaking(false) }
    }

    // MARK: - Friendly error page

    func noteHttpStatus(_ response: WKNavigationResponse) {
        guard let options = cfg.feature("errorPage"), response.isForMainFrame,
              let http = response.response as? HTTPURLResponse else { return }
        let code = http.statusCode
        if code >= 500 || (code == 404 && options.flag("notFound", false)) { httpErrorURL = http.url }
    }

    func errorPageNavigationStarted(_ url: URL?) {
        httpErrorURL = nil
        errorOverlay?.isHidden = true
    }

    func errorPageNavigationFinished(_ url: URL?) {
        if let failed = httpErrorURL {
            showErrorPage(failed)
        } else {
            errorOverlay?.isHidden = true
        }
        httpErrorURL = nil
    }

    private func showErrorPage(_ url: URL) {
        guard let options = cfg.feature("errorPage") else { return }
        if errorOverlay == nil {
            let overlay = UIView()
            overlay.backgroundColor = .systemBackground
            overlay.translatesAutoresizingMaskIntoConstraints = false
            view.insertSubview(overlay, aboveSubview: webView)
            NSLayoutConstraint.activate([
                overlay.leadingAnchor.constraint(equalTo: webView.leadingAnchor),
                overlay.trailingAnchor.constraint(equalTo: webView.trailingAnchor),
                overlay.topAnchor.constraint(equalTo: webView.topAnchor),
                overlay.bottomAnchor.constraint(equalTo: webView.bottomAnchor),
            ])
            let badge = UIView()
            badge.backgroundColor = theme.withAlphaComponent(0.12)
            badge.layer.cornerRadius = 48
            badge.translatesAutoresizingMaskIntoConstraints = false
            badge.widthAnchor.constraint(equalToConstant: 96).isActive = true
            badge.heightAnchor.constraint(equalToConstant: 96).isActive = true
            let icon = UIImageView(image: symbol("exclamationmark.circle", 40, .regular))
            icon.tintColor = theme.isLight ? .darkGray : theme
            icon.translatesAutoresizingMaskIntoConstraints = false
            badge.addSubview(icon)
            icon.centerXAnchor.constraint(equalTo: badge.centerXAnchor).isActive = true
            icon.centerYAnchor.constraint(equalTo: badge.centerYAnchor).isActive = true
            let title = UILabel()
            title.text = options.str("title", "Something went wrong")
            title.font = .systemFont(ofSize: 20, weight: .bold)
            title.textAlignment = .center
            title.numberOfLines = 0
            let message = UILabel()
            message.text = options.str("message", "We could not load this page. Please try again in a moment.")
            message.font = .systemFont(ofSize: 15)
            message.textColor = .secondaryLabel
            message.textAlignment = .center
            message.numberOfLines = 0
            let home = errorButton("Home", filled: false)
            home.addTarget(self, action: #selector(errorHomeTapped), for: .touchUpInside)
            let again = errorButton("Try again", filled: true)
            again.addTarget(self, action: #selector(errorRetryTapped), for: .touchUpInside)
            let row = UIStackView(arrangedSubviews: [home, again])
            row.spacing = 12
            let stack = UIStackView(arrangedSubviews: [badge, title, message, row])
            stack.axis = .vertical
            stack.alignment = .center
            stack.spacing = 10
            stack.setCustomSpacing(22, after: badge)
            stack.setCustomSpacing(26, after: message)
            stack.translatesAutoresizingMaskIntoConstraints = false
            overlay.addSubview(stack)
            NSLayoutConstraint.activate([
                stack.centerYAnchor.constraint(equalTo: overlay.centerYAnchor),
                stack.leadingAnchor.constraint(equalTo: overlay.leadingAnchor, constant: 32),
                stack.trailingAnchor.constraint(equalTo: overlay.trailingAnchor, constant: -32),
            ])
            overlay.accessibilityIdentifier = "ErrorPage"
            errorOverlay = overlay
        }
        errorOverlay?.accessibilityValue = url.absoluteString
        errorOverlay?.isHidden = false
        errorOverlay?.alpha = 0
        UIView.animate(withDuration: 0.22) { self.errorOverlay?.alpha = 1 }
    }

    private func errorButton(_ title: String, filled: Bool) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(title, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        b.backgroundColor = filled ? theme : theme.withAlphaComponent(0.12)
        b.setTitleColor(filled ? theme.onColor : (theme.isLight ? .darkGray : theme), for: .normal)
        b.layer.cornerRadius = 22
        b.contentEdgeInsets = UIEdgeInsets(top: 0, left: 24, bottom: 0, right: 24)
        b.heightAnchor.constraint(equalToConstant: 44).isActive = true
        return b
    }

    @objc private func errorHomeTapped() {
        errorOverlay?.isHidden = true
        webView.load(request(for: cfg.homeURL))
    }

    @objc private func errorRetryTapped() {
        let target = errorOverlay?.accessibilityValue.flatMap { URL(string: $0) } ?? cfg.homeURL
        webView.load(request(for: target))
    }

    // MARK: - Data saver: no pictures on mobile data or in Low Data Mode

    func startDataSaver() {
        guard cfg.feature("dataSaver") != nil else { return }
        let rules = #"[{"trigger":{"url-filter":".*","resource-type":["image"]},"action":{"type":"block"}}]"#
        WKContentRuleListStore.default().compileContentRuleList(forIdentifier: "w2a.dataSaver",
                                                                encodedContentRuleList: rules) { [weak self] list, _ in
            DispatchQueue.main.async {
                guard let self = self, let list = list else { return }
                self.dataSaverRules = list
                let monitor = NWPathMonitor()
                monitor.pathUpdateHandler = { [weak self] path in
                    let saving = path.isExpensive || path.isConstrained
                    DispatchQueue.main.async { self?.applyDataSaver(saving) }
                }
                monitor.start(queue: .global(qos: .utility))
                self.dataSaverMonitor = monitor
            }
        }
    }

    private func applyDataSaver(_ on: Bool) {
        guard let list = dataSaverRules, on != dataSaverActive else { return }
        dataSaverActive = on
        let ucc = webView.configuration.userContentController
        if on { ucc.add(list) } else { ucc.remove(list) }
    }

    // MARK: - Fresh page after a long break

    func reloadIfAwayLong() {
        guard let left = backgroundedAt else { return }
        backgroundedAt = nil
        guard let options = cfg.feature("resumeReload") else { return }
        let minutes = Double(max(1, options.int("minutes", 30)))
        if Date().timeIntervalSince(left) >= minutes * 60, webView.url != nil { webView.reload() }
    }

    // MARK: - Splash logo animation

    func animateSplashLogo(_ logo: UIView, _ style: String) {
        switch style {
        case "none":
            return
        case "rise":
            logo.alpha = 0
            logo.transform = CGAffineTransform(translationX: 0, y: 28)
            UIView.animate(withDuration: 0.65, delay: 0, options: .curveEaseOut) {
                logo.alpha = 1
                logo.transform = .identity
            }
        default:
            logo.alpha = 0
            logo.transform = CGAffineTransform(scaleX: 0.6, y: 0.6)
            UIView.animate(withDuration: 0.56, delay: 0, usingSpringWithDamping: 0.55, initialSpringVelocity: 0.8,
                           options: []) {
                logo.alpha = 1
                logo.transform = .identity
            }
            if style == "pulse" {
                let pulse = CABasicAnimation(keyPath: "transform.scale")
                pulse.fromValue = 1
                pulse.toValue = 1.07
                pulse.duration = 0.72
                pulse.autoreverses = true
                pulse.repeatCount = .infinity
                pulse.beginTime = CACurrentMediaTime() + 0.6
                pulse.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
                logo.layer.add(pulse, forKey: "w2a.pulse")
            }
        }
    }

    // MARK: - Lock screen in the app's colours

    func makeLockScreen() -> (view: UIView, fingerprint: UIButton, button: UIButton) {
        let light = theme.isLight
        let top = light ? theme : theme.mixed(with: .white, 0.10)
        let bottom = light ? theme.mixed(with: .black, 0.14) : theme.mixed(with: .black, 0.50)
        let fg: UIColor = light ? UIColor(white: 0.08, alpha: 1) : .white
        let overlay = GradientView()
        overlay.setColors(top, bottom)
        overlay.translatesAutoresizingMaskIntoConstraints = false

        // Logo with a soft shadow (the shadow sits on a holder, the picture is clipped round).
        let holder = UIView()
        holder.layer.shadowColor = UIColor.black.cgColor
        holder.layer.shadowOpacity = 0.25
        holder.layer.shadowRadius = 16
        holder.layer.shadowOffset = CGSize(width: 0, height: 8)
        holder.translatesAutoresizingMaskIntoConstraints = false
        holder.widthAnchor.constraint(equalToConstant: 92).isActive = true
        holder.heightAnchor.constraint(equalToConstant: 92).isActive = true
        let logo = UIImageView(image: UIImage(named: "SplashLogo"))
        logo.contentMode = .scaleAspectFill
        logo.clipsToBounds = true
        logo.layer.cornerRadius = 22
        logo.translatesAutoresizingMaskIntoConstraints = false
        holder.addSubview(logo)
        NSLayoutConstraint.activate([
            logo.leadingAnchor.constraint(equalTo: holder.leadingAnchor),
            logo.trailingAnchor.constraint(equalTo: holder.trailingAnchor),
            logo.topAnchor.constraint(equalTo: holder.topAnchor),
            logo.bottomAnchor.constraint(equalTo: holder.bottomAnchor),
        ])

        let name = UILabel()
        name.text = cfg.appName
        name.font = .systemFont(ofSize: 24, weight: .bold)
        name.textColor = fg
        name.textAlignment = .center
        let sub = UILabel()
        sub.text = "Locked for your privacy"
        sub.font = .systemFont(ofSize: 14)
        sub.textColor = fg.withAlphaComponent(0.7)

        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        let symbolName = context.biometryType == .faceID ? "faceid" : (context.biometryType == .touchID ? "touchid" : "lock.fill")
        let title = cfg.feature("appLock")?.str("title", "Unlock") ?? "Unlock"

        let circle = UIButton(type: .system)
        circle.setImage(symbol(symbolName, 36, .regular), for: .normal)
        circle.tintColor = fg
        circle.backgroundColor = fg.withAlphaComponent(0.12)
        circle.layer.cornerRadius = 42
        circle.layer.borderWidth = 1
        circle.layer.borderColor = fg.withAlphaComponent(0.25).cgColor
        circle.accessibilityLabel = context.biometryType == .faceID ? "Face ID" : (context.biometryType == .touchID ? "Touch ID" : "Passcode")
        circle.translatesAutoresizingMaskIntoConstraints = false
        circle.widthAnchor.constraint(equalToConstant: 84).isActive = true
        circle.heightAnchor.constraint(equalToConstant: 84).isActive = true

        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        button.backgroundColor = fg
        button.setTitleColor(light ? .white : bottom, for: .normal)
        button.layer.cornerRadius = 25
        button.contentEdgeInsets = UIEdgeInsets(top: 0, left: 30, bottom: 0, right: 30)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.heightAnchor.constraint(equalToConstant: 50).isActive = true
        button.widthAnchor.constraint(greaterThanOrEqualToConstant: 220).isActive = true
        button.accessibilityIdentifier = "UnlockButton"

        let stack = UIStackView(arrangedSubviews: [holder, name, sub, circle, button])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 6
        stack.setCustomSpacing(22, after: holder)
        stack.setCustomSpacing(56, after: sub)
        stack.setCustomSpacing(28, after: circle)
        stack.translatesAutoresizingMaskIntoConstraints = false
        overlay.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: overlay.centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: overlay.centerYAnchor),
            stack.leadingAnchor.constraint(greaterThanOrEqualTo: overlay.leadingAnchor, constant: 32),
        ])
        overlay.accessibilityViewIsModal = true
        overlay.accessibilityIdentifier = "LockScreen"

        // A calm entrance: the logo settles, the rest fades up after it.
        holder.alpha = 0
        holder.transform = CGAffineTransform(scaleX: 0.8, y: 0.8)
        UIView.animate(withDuration: 0.48, delay: 0, usingSpringWithDamping: 0.7, initialSpringVelocity: 0.6, options: []) {
            holder.alpha = 1
            holder.transform = .identity
        }
        for (i, item) in [name, sub, circle, button].enumerated() {
            item.alpha = 0
            item.transform = CGAffineTransform(translationX: 0, y: 12)
            UIView.animate(withDuration: 0.38, delay: 0.12 + Double(i + 1) * 0.05, options: .curveEaseOut) {
                item.alpha = 1
                item.transform = .identity
            }
        }
        return (overlay, circle, button)
    }
}
