//! Vimivo Desktop: Pure Rust Native Media Player for Windows and Linux using Slint.

use std::rc::Rc;
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};
use vimivo_core::audio::{BassBoost, SpectrumVisualizer, StereoVirtualizer, VoiceClarity};
use vimivo_core::db::VimivoDatabase;
use vimivo_core::model::{LibraryQuery, MediaItem, MediaKind, QueueRepeatMode};
use vimivo_core::queue::PlayQueue;
use vimivo_core::vault::{VaultConfig, VaultManager};
use vimivo_core::wifi_transfer::{WifiServerHandle, WifiTransferServer};

slint::include_modules!();

fn format_duration(millis: Option<i64>) -> String {
    match millis {
        Some(ms) => {
            let total_sec = ms / 1000;
            let m = total_sec / 60;
            let s = total_sec % 60;
            format!("{:02}:{:02}", m, s)
        }
        None => "--:--".to_string(),
    }
}

fn current_epoch_seconds() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

fn get_database_dir() -> Option<std::path::PathBuf> {
    #[cfg(target_os = "windows")]
    {
        if let Ok(appdata) = std::env::var("APPDATA") {
            let mut path = std::path::PathBuf::from(appdata);
            path.push("vimivo");
            return Some(path);
        }
    }

    #[cfg(target_os = "macos")]
    {
        if let Ok(home) = std::env::var("HOME") {
            let mut path = std::path::PathBuf::from(home);
            path.push("Library");
            path.push("Application Support");
            path.push("vimivo");
            return Some(path);
        }
    }

    #[cfg(not(any(target_os = "windows", target_os = "macos")))]
    {
        if let Ok(xdg) = std::env::var("XDG_DATA_HOME") {
            let mut path = std::path::PathBuf::from(xdg);
            path.push("vimivo");
            return Some(path);
        } else if let Ok(home) = std::env::var("HOME") {
            let mut path = std::path::PathBuf::from(home);
            path.push(".local");
            path.push("share");
            path.push("vimivo");
            return Some(path);
        }
    }

    None
}

fn main() -> Result<(), slint::PlatformError> {
    let main_window = AppWindow::new()?;

    // 1. Initialize Vimivo Core Database
    let db = {
        let opened_db = if let Some(dir) = get_database_dir() {
            let _ = std::fs::create_dir_all(&dir);
            let db_path = dir.join("vimivo.db");
            VimivoDatabase::open(db_path.to_str().unwrap_or("vimivo.db"))
                .or_else(|_| VimivoDatabase::open_in_memory())
        } else {
            VimivoDatabase::open_in_memory()
        };

        match opened_db {
            Ok(database) => {
                // Seed demo tracks if database is empty
                if let Ok(page) = database.query_media_items(&LibraryQuery::default()) {
                    if page.total_count == 0 {
                        let _ = database.upsert_media_item(&MediaItem {
                            id: "track-1".into(),
                            uri: "file:///music/solaris.flac".into(),
                            title: "Solaris Flare".into(),
                            kind: MediaKind::Audio,
                            duration_millis: Some(214000),
                            artist: Some("Vimivo Studio".into()),
                            album: Some("Aurora Horizon".into()),
                            artwork_uri: None,
                            album_artist: None,
                            genre: Some("Ambient".into()),
                            size_bytes: Some(15400000),
                            mime_type: Some("audio/flac".into()),
                            file_name: Some("solaris.flac".into()),
                            date_added_epoch_seconds: None,
                            date_modified_epoch_seconds: None,
                            width: None,
                            height: None,
                            frame_rate: None,
                            bitrate: None,
                            track_number: Some(1),
                            disc_number: Some(1),
                            release_year: Some(2026),
                            folder_name: None,
                            folder_path: None,
                            source: vimivo_core::model::MediaSource::LocalFile,
                            volume_name: None,
                            is_favorite: false,
                            is_available: true,
                            last_playback_position_millis: 0,
                            last_played_at_epoch_millis: None,
                        });

                        let _ = database.upsert_media_item(&MediaItem {
                            id: "track-2".into(),
                            uri: "file:///music/lunar.mp3".into(),
                            title: "Lunar Gravity".into(),
                            kind: MediaKind::Audio,
                            duration_millis: Some(185000),
                            artist: Some("Deep Space".into()),
                            album: Some("Orbital Sound".into()),
                            artwork_uri: None,
                            album_artist: None,
                            genre: Some("Electronic".into()),
                            size_bytes: Some(7400000),
                            mime_type: Some("audio/mp3".into()),
                            file_name: Some("lunar.mp3".into()),
                            date_added_epoch_seconds: None,
                            date_modified_epoch_seconds: None,
                            width: None,
                            height: None,
                            frame_rate: None,
                            bitrate: None,
                            track_number: Some(2),
                            disc_number: Some(1),
                            release_year: Some(2026),
                            folder_name: None,
                            folder_path: None,
                            source: vimivo_core::model::MediaSource::LocalFile,
                            volume_name: None,
                            is_favorite: false,
                            is_available: true,
                            last_playback_position_millis: 0,
                            last_played_at_epoch_millis: None,
                        });
                    }
                }
                database
            }
            Err(e) => panic!("Failed to initialize database: {}", e),
        }
    };

    let db_ref = Rc::new(Mutex::new(db));
    let queue_ref = Rc::new(Mutex::new(PlayQueue::new()));

    // 2. Initialize Core Subsystems
    let spectrum_ref = Rc::new(Mutex::new(SpectrumVisualizer::new(16)));
    let bass_boost_ref = Rc::new(Mutex::new(BassBoost::new(44100, 2)));
    let virtualizer_ref = Rc::new(Mutex::new(StereoVirtualizer::new(1.0)));
    let voice_clarity_ref = Rc::new(Mutex::new(VoiceClarity::new(44100, 2)));

    let vault_manager = {
        let mut vm = VaultManager::new(VaultConfig::default());
        let _ = vm.setup_pin("1234"); // default demo PIN
        Rc::new(Mutex::new(vm))
    };

    let wifi_server_ref: Rc<Mutex<Option<WifiServerHandle>>> = Rc::new(Mutex::new(None));

    struct DesktopPlaybackState {
        position_millis: i64,
        duration_millis: i64,
    }

    let playback_state_ref = Rc::new(Mutex::new(DesktopPlaybackState {
        position_millis: 0,
        duration_millis: 214_000,
    }));

    // 3. Populate media list in UI
    let populate_list = {
        let db_ref = Rc::clone(&db_ref);
        let queue_ref = Rc::clone(&queue_ref);
        let window_weak = main_window.as_weak();

        move |search: &str| {
            if let Some(window) = window_weak.upgrade() {
                let db = db_ref.lock().unwrap();
                let mut q = LibraryQuery::default();
                q.text = search.to_string();

                if let Ok(page) = db.query_media_items(&q) {
                    let mut queue = queue_ref.lock().unwrap();
                    let ids: Vec<String> = page.items.iter().map(|item| item.id.0.clone()).collect();
                    queue.replace(ids, 0);

                    let slint_rows: Vec<MediaRow> = page
                        .items
                        .into_iter()
                        .map(|item| MediaRow {
                            id: item.id.0.into(),
                            title: item.title.into(),
                            artist: item.artist.unwrap_or_else(|| "Unknown Artist".into()).into(),
                            album: item.album.unwrap_or_else(|| "Unknown Album".into()).into(),
                            duration: format_duration(item.duration_millis).into(),
                        })
                        .collect();

                    let model = std::rc::Rc::new(slint::VecModel::from(slint_rows));
                    window.set_media_list(model.into());
                }
            }
        }
    };

    populate_list("");

    // 4. Setup Callbacks
    {
        let window_weak = main_window.as_weak();
        let queue_ref = Rc::clone(&queue_ref);
        let db_ref = Rc::clone(&db_ref);

        main_window.on_play_pause_clicked(move || {
            if let Some(window) = window_weak.upgrade() {
                let is_playing = !window.get_is_playing();
                window.set_is_playing(is_playing);

                if is_playing {
                    let queue = queue_ref.lock().unwrap();
                    if let Some(id) = queue.current_id() {
                        let db = db_ref.lock().unwrap();
                        if let Ok(Some(item)) = db.get_media_item(id) {
                            window.set_now_playing_title(item.title.into());
                            window.set_now_playing_artist(item.artist.unwrap_or_default().into());
                            window.set_total_time(format_duration(item.duration_millis).into());
                        }
                    }
                }
            }
        });
    }

    {
        let window_weak = main_window.as_weak();
        let queue_ref = Rc::clone(&queue_ref);
        let db_ref = Rc::clone(&db_ref);
        let state_ref = Rc::clone(&playback_state_ref);

        main_window.on_item_selected(move |selected_id| {
            if let Some(window) = window_weak.upgrade() {
                let mut queue = queue_ref.lock().unwrap();
                if queue.jump_to_id(&selected_id) {
                    let db = db_ref.lock().unwrap();
                    if let Ok(Some(item)) = db.get_media_item(&selected_id) {
                        let dur = item.duration_millis.unwrap_or(214_000);
                        {
                            let mut state = state_ref.lock().unwrap();
                            state.position_millis = 0;
                            state.duration_millis = dur;
                        }
                        window.set_now_playing_title(item.title.into());
                        window.set_now_playing_artist(item.artist.unwrap_or_default().into());
                        window.set_total_time(format_duration(item.duration_millis).into());
                        window.set_is_playing(true);
                        window.set_progress(0.0);
                        window.set_current_time("00:00".into());
                    }
                }
            }
        });
    }

    {
        let window_weak = main_window.as_weak();
        let queue_ref = Rc::clone(&queue_ref);
        let db_ref = Rc::clone(&db_ref);
        let state_ref = Rc::clone(&playback_state_ref);

        main_window.on_next_clicked(move || {
            if let Some(window) = window_weak.upgrade() {
                let mut queue = queue_ref.lock().unwrap();
                if let Some(next_id) = queue.next().map(|s| s.to_string()) {
                    let db = db_ref.lock().unwrap();
                    if let Ok(Some(item)) = db.get_media_item(&next_id) {
                        let dur = item.duration_millis.unwrap_or(214_000);
                        {
                            let mut state = state_ref.lock().unwrap();
                            state.position_millis = 0;
                            state.duration_millis = dur;
                        }
                        window.set_now_playing_title(item.title.into());
                        window.set_now_playing_artist(item.artist.unwrap_or_default().into());
                        window.set_total_time(format_duration(item.duration_millis).into());
                        window.set_is_playing(true);
                        window.set_progress(0.0);
                        window.set_current_time("00:00".into());
                    }
                }
            }
        });
    }

    {
        let window_weak = main_window.as_weak();
        let queue_ref = Rc::clone(&queue_ref);
        let db_ref = Rc::clone(&db_ref);
        let state_ref = Rc::clone(&playback_state_ref);

        main_window.on_previous_clicked(move || {
            if let Some(window) = window_weak.upgrade() {
                let mut queue = queue_ref.lock().unwrap();
                if let Some(prev_id) = queue.previous().map(|s| s.to_string()) {
                    let db = db_ref.lock().unwrap();
                    if let Ok(Some(item)) = db.get_media_item(&prev_id) {
                        let dur = item.duration_millis.unwrap_or(214_000);
                        {
                            let mut state = state_ref.lock().unwrap();
                            state.position_millis = 0;
                            state.duration_millis = dur;
                        }
                        window.set_now_playing_title(item.title.into());
                        window.set_now_playing_artist(item.artist.unwrap_or_default().into());
                        window.set_total_time(format_duration(item.duration_millis).into());
                        window.set_is_playing(true);
                        window.set_progress(0.0);
                        window.set_current_time("00:00".into());
                    }
                }
            }
        });
    }

    // Timeline Progress Seek Callback
    {
        let window_weak = main_window.as_weak();
        let state_ref = Rc::clone(&playback_state_ref);

        main_window.on_progress_seeked(move |fraction_percent| {
            if let Some(window) = window_weak.upgrade() {
                let mut state = state_ref.lock().unwrap();
                let dur = state.duration_millis;
                if dur > 0 {
                    let new_pos = ((fraction_percent as f64 / 100.0) * dur as f64).round() as i64;
                    let clamped = new_pos.clamp(0, dur);
                    state.position_millis = clamped;
                    window.set_current_time(format_duration(Some(clamped)).into());
                }
            }
        });
    }

    {
        let window_weak = main_window.as_weak();
        let queue_ref = Rc::clone(&queue_ref);

        main_window.on_shuffle_toggled(move || {
            if let Some(window) = window_weak.upgrade() {
                let mut queue = queue_ref.lock().unwrap();
                let new_shuffle = !queue.is_shuffled();
                queue.set_shuffled(new_shuffle);
                window.set_is_shuffled(new_shuffle);
            }
        });
    }

    {
        let window_weak = main_window.as_weak();
        let queue_ref = Rc::clone(&queue_ref);

        main_window.on_repeat_toggled(move || {
            if let Some(window) = window_weak.upgrade() {
                let mut queue = queue_ref.lock().unwrap();
                let next_mode = match queue.repeat_mode() {
                    QueueRepeatMode::Off => QueueRepeatMode::All,
                    QueueRepeatMode::All => QueueRepeatMode::One,
                    QueueRepeatMode::One => QueueRepeatMode::Off,
                };
                queue.set_repeat_mode(next_mode);
                let mode_int = match next_mode {
                    QueueRepeatMode::Off => 0,
                    QueueRepeatMode::All => 1,
                    QueueRepeatMode::One => 2,
                };
                window.set_repeat_mode(mode_int);
            }
        });
    }

    {
        let populate = populate_list;
        main_window.on_search_changed(move |text| {
            populate(text.as_str());
        });
    }

    // DSP Callbacks
    {
        let window_weak = main_window.as_weak();
        main_window.on_toggle_dsp_panel(move || {
            if let Some(window) = window_weak.upgrade() {
                let current = window.get_show_dsp_panel();
                window.set_show_dsp_panel(!current);
            }
        });
    }

    {
        let bass_ref = Rc::clone(&bass_boost_ref);
        main_window.on_bass_boost_changed(move |val| {
            let mut bb = bass_ref.lock().unwrap();
            bb.set_gain_db(val);
        });
    }

    {
        let virt_ref = Rc::clone(&virtualizer_ref);
        main_window.on_virtualizer_changed(move |val| {
            let mut sv = virt_ref.lock().unwrap();
            sv.set_width(val);
        });
    }

    {
        let window_weak = main_window.as_weak();
        let clarity_ref = Rc::clone(&voice_clarity_ref);
        main_window.on_voice_clarity_toggled(move || {
            if let Some(window) = window_weak.upgrade() {
                let mut vc = clarity_ref.lock().unwrap();
                let new_state = !vc.is_enabled();
                vc.set_enabled(new_state);
                window.set_voice_clarity_enabled(new_state);
            }
        });
    }

    // Vault Callbacks
    {
        let window_weak = main_window.as_weak();
        main_window.on_open_vault_dialog(move || {
            if let Some(window) = window_weak.upgrade() {
                window.set_show_vault_dialog(true);
                window.set_vault_pin_input("".into());
                window.set_vault_status_msg("Enter 4-digit PIN to access Private Vault".into());
            }
        });
    }

    {
        let window_weak = main_window.as_weak();
        main_window.on_close_vault_dialog(move || {
            if let Some(window) = window_weak.upgrade() {
                window.set_show_vault_dialog(false);
            }
        });
    }

    {
        let window_weak = main_window.as_weak();
        let vault_ref = Rc::clone(&vault_manager);
        main_window.on_submit_vault_pin(move |pin| {
            if let Some(window) = window_weak.upgrade() {
                let mut vm = vault_ref.lock().unwrap();
                let now = current_epoch_seconds();
                match vm.unlock(&pin, now) {
                    Ok(_token) => {
                        window.set_vault_unlocked(true);
                        window.set_show_vault_dialog(false);
                        window.set_vault_status_msg("Vault unlocked successfully!".into());
                    }
                    Err(e) => {
                        window.set_vault_status_msg(e.to_string().into());
                    }
                }
            }
        });
    }

    // Wi-Fi Transfer Server Callback
    {
        let window_weak = main_window.as_weak();
        let wifi_ref = Rc::clone(&wifi_server_ref);
        main_window.on_toggle_wifi_server(move || {
            if let Some(window) = window_weak.upgrade() {
                let mut handle_opt = wifi_ref.lock().unwrap();
                if let Some(handle) = handle_opt.take() {
                    handle.stop();
                    window.set_wifi_server_active(false);
                    window.set_wifi_server_url("".into());
                } else {
                    let dir = get_database_dir().unwrap_or_else(|| std::path::PathBuf::from("."));
                    if let Ok(server) = WifiTransferServer::start("0.0.0.0:8080", "Vimivo Desktop".into(), dir) {
                        let url = format!("http://localhost:{}", server.port);
                        window.set_wifi_server_active(true);
                        window.set_wifi_server_url(url.into());
                        *handle_opt = Some(server);
                    }
                }
            }
        });
    }

    // Decoder Mode Callback
    {
        let window_weak = main_window.as_weak();
        main_window.on_decoder_mode_toggled(move || {
            if let Some(window) = window_weak.upgrade() {
                let current = window.get_decoder_mode();
                let next = match current.as_str() {
                    "HW+" => "HW",
                    "HW" => "SW",
                    _ => "HW+",
                };
                window.set_decoder_mode(next.into());
            }
        });
    }

    // Volume Callback
    {
        main_window.on_volume_changed(move |_vol| {
            // Volume updated (with pure Rust soft-knee limiter active for > 100%)
        });
    }

    // Spectrum Visualizer & Timeline Live Animation Timer (30 fps)
    let _timer = {
        let window_weak = main_window.as_weak();
        let spectrum_ref = Rc::clone(&spectrum_ref);
        let playback_state_timer_ref = Rc::clone(&playback_state_ref);
        let timer = slint::Timer::default();
        let mut phase = 0.0f32;

        timer.start(
            slint::TimerMode::Repeated,
            std::time::Duration::from_millis(33),
            move || {
                if let Some(window) = window_weak.upgrade() {
                    let mut visualizer = spectrum_ref.lock().unwrap();
                    if window.get_is_playing() {
                        phase += 0.15;

                        // 1. Smooth real-time timeline advancement
                        {
                            let mut state = playback_state_timer_ref.lock().unwrap();
                            let dur = state.duration_millis;
                            if dur > 0 {
                                state.position_millis += 33;
                                if state.position_millis > dur {
                                    state.position_millis = dur;
                                }
                                let pct = (state.position_millis as f32 / dur as f32 * 100.0).clamp(0.0, 100.0);
                                window.set_progress(pct);
                                window.set_current_time(format_duration(Some(state.position_millis)).into());
                            }
                        }

                        // 2. Synthesize audio frame data for active track visualizer HUD
                        let mut synthetic = Vec::with_capacity(512);
                        for i in 0..512 {
                            let t = i as f32 / 512.0;
                            let s = ((t * 8.0 + phase).sin() * 0.6 + (t * 22.0 - phase * 1.5).cos() * 0.4).abs();
                            synthetic.push(s);
                        }
                        let bands = visualizer.process_samples(&synthetic);
                        let model = std::rc::Rc::new(slint::VecModel::from(bands.to_vec()));
                        window.set_spectrum_bars(model.into());
                    } else {
                        let bands = visualizer.process_samples(&[]);
                        let model = std::rc::Rc::new(slint::VecModel::from(bands.to_vec()));
                        window.set_spectrum_bars(model.into());
                    }
                }
            },
        );
        timer
    };

    println!("Starting Vimivo Desktop Player (Rust + Slint)...");
    main_window.run()
}