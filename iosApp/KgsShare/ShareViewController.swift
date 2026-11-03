import UIKit
import UniformTypeIdentifiers

/// The "KGS Calendar" share target, as Android's ShareToCalendarActivity: it reads only the text
/// of a share (subject, text, a link) and opens the app's event editor prefilled with it. Nothing
/// is saved until the event is saved in the app.
final class ShareViewController: UIViewController {
    private static let maxTextLength = 50_000
    private var handled = false

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !handled else { return }
        handled = true
        Task { await share() }
    }

    @MainActor
    private func share() async {
        let items = extensionContext?.inputItems.compactMap { $0 as? NSExtensionItem } ?? []
        var subject: String?
        var parts: [String] = []
        for item in items {
            if subject == nil, let title = item.attributedTitle?.string, !title.isEmpty { subject = title }
            if let content = item.attributedContentText?.string, !content.isEmpty { parts.append(content) }
            for provider in item.attachments ?? [] {
                if let text = await load(provider, type: UTType.plainText) as? String {
                    parts.append(text)
                } else if let url = await load(provider, type: UTType.url) as? URL {
                    parts.append(url.absoluteString)
                }
            }
        }
        // The page title often comes as text too; it is already the subject (Android's EXTRA_SUBJECT).
        var seen = Set<String>(subject.map { [$0.trimmingCharacters(in: .whitespacesAndNewlines)] } ?? [])
        let text = parts
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && seen.insert($0).inserted }
            .joined(separator: "\n\n")
        var components = URLComponents()
        components.scheme = "kgscalendar"
        components.host = "share"
        components.queryItems = [
            subject.map { URLQueryItem(name: "subject", value: $0) },
            URLQueryItem(name: "text", value: String(text.prefix(Self.maxTextLength))),
        ].compactMap { $0 }
        guard let url = components.url, openApp(url) else {
            showFailure()
            return
        }
        extensionContext?.completeRequest(returningItems: nil)
    }

    private func load(_ provider: NSItemProvider, type: UTType) async -> NSSecureCoding? {
        guard provider.hasItemConformingToTypeIdentifier(type.identifier) else { return nil }
        return try? await provider.loadItem(forTypeIdentifier: type.identifier)
    }

    /// Share extensions have no API to open their app; the app object is found along the responder
    /// chain and asked through the Objective-C runtime (its `open` is not available to extensions).
    private func openApp(_ url: URL) -> Bool {
        typealias OpenURL = @convention(c) (AnyObject, Selector, NSURL, NSDictionary, AnyObject?) -> Void
        let selector = NSSelectorFromString("openURL:options:completionHandler:")
        var responder: UIResponder? = self
        while let current = responder {
            if current is UIApplication, current.responds(to: selector) {
                let open = unsafeBitCast(current.method(for: selector), to: OpenURL.self)
                open(current, selector, url as NSURL, NSDictionary(), nil)
                return true
            }
            responder = current.next
        }
        return false
    }

    private func showFailure() {
        let german = Locale.preferredLanguages.first?.hasPrefix("de") == true
        let alert = UIAlertController(
            title: "KGS Calendar",
            message: german ? "KGS Calendar konnte nicht geöffnet werden." : "KGS Calendar could not be opened.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "OK", style: .default) { [weak self] _ in
            self?.extensionContext?.completeRequest(returningItems: nil)
        })
        present(alert, animated: true)
    }
}
