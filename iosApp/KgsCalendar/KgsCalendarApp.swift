import SwiftUI
import KgsSharedUi

@main
struct KgsCalendarApp: App {
    init() {
        MainViewControllerKt.onAppLaunch()
    }

    var body: some Scene {
        WindowGroup {
            CalendarRoot()
                .ignoresSafeArea()
                // Calendar files opened with the app (Files, Mail, the share sheet), as Android's IcsImportActivity.
                .onOpenURL { url in
                    // Text shared with the KgsShare extension, as Android's share target.
                    if url.scheme == "kgscalendar" {
                        guard url.host == "share" else { return }
                        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
                        MainViewControllerKt.openSharedText(
                            subject: items.first { $0.name == "subject" }?.value,
                            text: items.first { $0.name == "text" }?.value
                        )
                        return
                    }
                    guard url.isFileURL else { return }
                    let scoped = url.startAccessingSecurityScopedResource()
                    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    // Read from a private copy: the original may only be readable during this call.
                    let copy = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "-" + url.lastPathComponent)
                    guard (try? FileManager.default.copyItem(at: url, to: copy)) != nil else { return }
                    // Files handed over by other apps land in Documents/Inbox; the private copy replaces them.
                    if url.deletingLastPathComponent().lastPathComponent == "Inbox" { try? FileManager.default.removeItem(at: url) }
                    MainViewControllerKt.openFile(path: copy.path)
                }
        }
    }
}

private struct CalendarRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
