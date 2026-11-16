//! Vimivo Desktop: Pure Rust Native Media Player for Windows and Linux using Slint.

use std::rc::Rc;
use std::sync::Mutex;
use vimivo_core::db::VimivoDatabase;
use vimivo_core::model::{LibraryQuery, MediaItem, MediaKind, QueueRepeatMode};
use vimivo_core::queue::PlayQueue;

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

    // 1. Initialize Vimivo Core Database (persistent under user data directory, with in-memory fallback)
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

    // 2. Populate media list in UI
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

    // 3. Setup Callbacks
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

        main_window.on_item_selected(move |selected_id| {
            if let Some(window) = window_weak.upgrade() {
                let mut queue = queue_ref.lock().unwrap();
                if queue.jump_to_id(&selected_id) {
                    let db = db_ref.lock().unwrap();
                    if let Ok(Some(item)) = db.get_media_item(&selected_id) {
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

        main_window.on_next_clicked(move || {
            if let Some(window) = window_weak.upgrade() {
                let mut queue = queue_ref.lock().unwrap();
                if let Some(next_id) = queue.next().map(|s| s.to_string()) {
                    let db = db_ref.lock().unwrap();
                    if let Ok(Some(item)) = db.get_media_item(&next_id) {
                        window.set_now_playing_title(item.title.into());
                        window.set_now_playing_artist(item.artist.unwrap_or_default().into());
                        window.set_total_time(format_duration(item.duration_millis).into());
                        window.set_is_playing(true);
                        window.set_progress(0.0);
                    }
                }
            }
        });
    }

    {
        let window_weak = main_window.as_weak();
        let queue_ref = Rc::clone(&queue_ref);
        let db_ref = Rc::clone(&db_ref);

        main_window.on_previous_clicked(move || {
            if let Some(window) = window_weak.upgrade() {
                let mut queue = queue_ref.lock().unwrap();
                if let Some(prev_id) = queue.previous().map(|s| s.to_string()) {
                    let db = db_ref.lock().unwrap();
                    if let Ok(Some(item)) = db.get_media_item(&prev_id) {
                        window.set_now_playing_title(item.title.into());
                        window.set_now_playing_artist(item.artist.unwrap_or_default().into());
                        window.set_total_time(format_duration(item.duration_millis).into());
                        window.set_is_playing(true);
                        window.set_progress(0.0);
                    }
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

    println!("Starting Vimivo Desktop Player (Rust + Slint)...");
    // In headless or CI environments, we can run or instantiate
    main_window.run()
}
