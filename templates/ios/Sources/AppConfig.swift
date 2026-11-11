import UIKit

/// Reads config.json, which the build writes into the app bundle.
struct AppConfig {
    static let shared = AppConfig()

    private let raw: [String: Any]

    private init() {
        var dict: [String: Any] = [:]
        if let url = Bundle.main.url(forResource: "config", withExtension: "json"),
           let data = try? Data(contentsOf: url),
           let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            dict = obj
        }
        raw = dict
    }

    func string(_ key: String, _ fallback: String = "") -> String {
        return (raw[key] as? String) ?? fallback
    }

    func bool(_ key: String, _ fallback: Bool) -> Bool {
        return (raw[key] as? Bool) ?? fallback
    }

    func has(_ permission: String) -> Bool {
        return ((raw["permissions"] as? [String]) ?? []).contains(permission)
    }

    /// Options of an extra feature, or nil when the feature is off.
    func feature(_ id: String) -> [String: Any]? {
        guard let all = raw["features"] as? [String: Any],
              let f = all[id] as? [String: Any],
              (f["enabled"] as? Bool) == true else { return nil }
        return f
    }

    var homeURL: URL {
        return URL(string: string("url")) ?? URL(string: "about:blank")!
    }

    var appName: String {
        return string("appName", Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "App")
    }

    var internalHosts: [String] {
        var hosts = ((raw["internalHosts"] as? [String]) ?? []).map { AppConfig.stripWww($0.lowercased()) }
        if let host = homeURL.host { hosts.append(AppConfig.stripWww(host.lowercased())) }
        return hosts
    }

    static func stripWww(_ host: String) -> String {
        return host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
    }

    func color(_ key: String, _ fallback: String) -> UIColor {
        return AppConfig.parse(string(key, fallback)) ?? AppConfig.parse(fallback) ?? .white
    }

    static func parse(_ hex: String) -> UIColor? {
        var s = hex.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        return UIColor(red: CGFloat((v >> 16) & 0xFF) / 255.0,
                       green: CGFloat((v >> 8) & 0xFF) / 255.0,
                       blue: CGFloat(v & 0xFF) / 255.0,
                       alpha: 1)
    }
}

/// Typed reads from a feature's options.
extension Dictionary where Key == String, Value == Any {
    func str(_ key: String, _ fallback: String = "") -> String {
        let s = (self[key] as? String) ?? ""
        return s.isEmpty ? fallback : s
    }

    func int(_ key: String, _ fallback: Int) -> Int {
        if let n = self[key] as? Int { return n }
        if let d = self[key] as? Double { return Int(d) }
        if let s = self[key] as? String, let n = Int(s) { return n }
        return fallback
    }

    func flag(_ key: String, _ fallback: Bool) -> Bool {
        return (self[key] as? Bool) ?? fallback
    }

    func color(_ key: String) -> UIColor? {
        return AppConfig.parse(str(key))
    }

    func list(_ key: String) -> [[String: Any]] {
        return (self[key] as? [[String: Any]]) ?? []
    }

    func strings(_ key: String) -> [String] {
        return (self[key] as? [String]) ?? []
    }
}

extension UIColor {
    var isLight: Bool {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        return (0.299 * r + 0.587 * g + 0.114 * b) > 0.63
    }

    /// Readable text colour on top of this colour.
    var onColor: UIColor {
        return isLight ? UIColor(white: 0.12, alpha: 1) : .white
    }
}
