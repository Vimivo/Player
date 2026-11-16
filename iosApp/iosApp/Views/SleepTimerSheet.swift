import SwiftUI
import VimivoCoreRust

/// Choose when playback fades out and stops: a quick choice, a custom time, or after the current track.
struct SleepTimerSheet: View {
    @ObservedObject var timer: SleepTimerModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            SleepTimerContent(timer: timer, onDone: { dismiss() })
                .navigationTitle("Sleep timer")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("Done") { dismiss() } } }
        }
    }
}

/// The sleep timer list, shared by the music player sheet and the video player's settings.
struct SleepTimerContent: View {
    @ObservedObject var timer: SleepTimerModel
    let onDone: () -> Void
    @State private var hours = 0
    @State private var minutes = 30
    @State private var showInvalid = false

    private var customMinutes: Int? {
        parseCustomSleepTimerMinutes(hours: "\(hours)", minutes: "\(minutes)").map(Int.init)
    }

    var body: some View {
        List {
            if timer.isActive {
                Section {
                    Label(status, systemImage: "moon.zzz.fill").foregroundColor(.accentColor)
                    Button("Cancel sleep timer", role: .destructive) { timer.cancel(); onDone() }
                }
            }
            Section("Stop after") {
                ForEach(SleepTimerModel.minuteChoices, id: \.self) { choice in
                    Button(formatSleepTimerLabel(minutes: Int32(choice))) { timer.start(minutes: choice); onDone() }
                }
                Button("The current track") { timer.stopAfterCurrentTrack(); onDone() }
            }
            Section("Custom time") {
                HStack(spacing: 0) {
                    Picker("Hours", selection: $hours) {
                        ForEach(0..<24, id: \.self) { Text("\($0) h").tag($0) }
                    }
                    .pickerStyle(.wheel)
                    Picker("Minutes", selection: $minutes) {
                        ForEach(0..<60, id: \.self) { Text("\($0) min").tag($0) }
                    }
                    .pickerStyle(.wheel)
                }
                .frame(height: 120)
                Button("Start \(customMinutes.map { formatSleepTimerLabel(minutes: Int32($0)) } ?? "timer")") {
                    if let minutes = customMinutes { timer.start(minutes: minutes); onDone() } else { showInvalid = true }
                }
                if showInvalid && customMinutes == nil {
                    Text("Choose at least 1 minute.").font(.footnote).foregroundColor(.red)
                }
            }
            Section(footer: Text("The sound fades out over \(timer.fadeSeconds) seconds, then playback pauses. It works with the screen off.")) { EmptyView() }
        }
    }

    private var status: String {
        if let remaining = timer.remainingMillis { return "Stops in \(formatClock(millis: remaining))" }
        return "Stops after this track"
    }
}
