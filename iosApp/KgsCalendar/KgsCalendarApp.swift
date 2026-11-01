import SwiftUI
import KgsSharedUi

@main
struct KgsCalendarApp: App {
    var body: some Scene {
        WindowGroup {
            CalendarRoot().ignoresSafeArea()
        }
    }
}

private struct CalendarRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
