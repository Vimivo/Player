import SwiftUI

/// Choose when playback fades out and stops: after 15 to 60 minutes, or after the current track.
struct SleepTimerSheet: View {
    @ObservedObject var timer: SleepTimerModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            List {
                if timer.isActive {
                    Section {
                        Label(status, systemImage: "moon.zzz.fill").foregroundColor(.accentColor)
                        Button("Cancel sleep timer", role: .destructive) { timer.cancel(); dismiss() }
                    }
                }
                Section("Stop after") {
                    ForEach([15, 30, 45, 60], id: \.self) { minutes in
                        Button("\(minutes) minutes") { timer.start(minutes: minutes); dismiss() }
                    }
                    Button("The current track") { timer.stopAfterCurrentTrack(); dismiss() }
                }
                Section(footer: Text("The sound fades out over \(timer.fadeSeconds) seconds, then playback pauses. It works with the screen off.")) { EmptyView() }
            }
            .navigationTitle("Sleep timer")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("Done") { dismiss() } } }
        }
    }

    private var status: String {
        if let remaining = timer.remainingMillis { return "Stops in \(formatClock(millis: remaining))" }
        return "Stops after this track"
    }
}
