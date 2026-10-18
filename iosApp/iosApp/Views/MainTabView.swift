import SwiftUI

public struct MainTabView: View {
    @ObservedObject public var engine: AVPlayerPlaybackEngine
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var selectedTab = 0
    @State private var showNowPlayingModal = false
    @State private var showVideoPlayer = false

    public init(engine: AVPlayerPlaybackEngine) {
        self.engine = engine
    }

    public var body: some View {
        Group {
            if horizontalSizeClass == .regular {
                ipadLayout
            } else {
                iphoneLayout
            }
        }
        .sheet(isPresented: $showNowPlayingModal) {
            NowPlayingView(engine: engine)
        }
        .fullScreenCover(isPresented: $showVideoPlayer) {
            VideoPlayerView(engine: engine) {
                showVideoPlayer = false
                engine.pause()
            }
        }
    }

    /// Starts a video queue and opens the full-screen player, resuming where the viewer left off.
    private func openVideo(_ items: [MediaItem], _ index: Int, _ resumeMillis: Int64) {
        engine.playQueue(items: items, startIndex: index)
        if resumeMillis > 0 {
            engine.seek(toMillis: resumeMillis)
        }
        showVideoPlayer = true
    }

    private func expandNowPlaying() {
        if engine.state.currentItem?.kind == .video {
            showVideoPlayer = true
        } else {
            showNowPlayingModal = true
        }
    }

    private var iphoneLayout: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: $selectedTab) {
                HomeView(engine: engine, onSelectTab: { tab in selectedTab = tab }, onOpenVideo: openVideo)
                    .tabItem {
                        Label("Home", systemImage: "house.fill")
                    }
                    .tag(0)

                MusicView(engine: engine)
                    .tabItem {
                        Label("Music", systemImage: "music.note")
                    }
                    .tag(1)

                VideoView(engine: engine, onOpenVideo: openVideo)
                    .tabItem {
                        Label("Videos", systemImage: "film")
                    }
                    .tag(2)

                PlaylistView(engine: engine)
                    .tabItem {
                        Label("Playlists", systemImage: "music.note.list")
                    }
                    .tag(3)

                SettingsView()
                    .tabItem {
                        Label("Settings", systemImage: "gearshape")
                    }
                    .tag(4)
            }

            NowPlayingBar(engine: engine, onExpand: expandNowPlaying)
            .padding(.bottom, 54)
        }
    }

    private var ipadLayout: some View {
        NavigationSplitView {
            List {
                Button(action: { selectedTab = 0 }) {
                    Label("Home", systemImage: "house.fill")
                        .foregroundColor(selectedTab == 0 ? .accentColor : .primary)
                }
                Button(action: { selectedTab = 1 }) {
                    Label("Music", systemImage: "music.note")
                        .foregroundColor(selectedTab == 1 ? .accentColor : .primary)
                }
                Button(action: { selectedTab = 2 }) {
                    Label("Videos", systemImage: "film")
                        .foregroundColor(selectedTab == 2 ? .accentColor : .primary)
                }
                Button(action: { selectedTab = 3 }) {
                    Label("Playlists", systemImage: "music.note.list")
                        .foregroundColor(selectedTab == 3 ? .accentColor : .primary)
                }
                Button(action: { selectedTab = 4 }) {
                    Label("Settings", systemImage: "gearshape")
                        .foregroundColor(selectedTab == 4 ? .accentColor : .primary)
                }
            }
            .listStyle(.sidebar)
            .navigationTitle("Vimivo")
        } detail: {
            ZStack(alignment: .bottom) {
                Group {
                    switch selectedTab {
                    case 0: HomeView(engine: engine, onSelectTab: { tab in selectedTab = tab }, onOpenVideo: openVideo)
                    case 1: MusicView(engine: engine)
                    case 2: VideoView(engine: engine, onOpenVideo: openVideo)
                    case 3: PlaylistView(engine: engine)
                    default: SettingsView()
                    }
                }

                NowPlayingBar(engine: engine, onExpand: expandNowPlaying)
                .padding(.bottom, 16)
            }
        }
    }
}
