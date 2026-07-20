package dev.vxs.frostsoul.lyrics

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vxs.frostsoul.db.DatabaseDao
import dev.vxs.frostsoul.db.entities.LyricsEntity
import dev.vxs.frostsoul.db.entities.Song
import dev.vxs.frostsoul.lyrics.model.LyricsLoadState
import dev.vxs.frostsoul.lyrics.model.LyricsPlaybackState
import dev.vxs.frostsoul.lyrics.model.SyncedLyrics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized LyricsManager — single source of truth for all lyric synchronization.
 * 
 * Consumed by:
 * - Full Player (LyricsScreen)
 * - Mini Player
 * - Floating Desktop Overlay
 * - Notification Panel
 * - Lock Screen (via MediaSession)
 * 
 * All components receive updates from the same source for perfect sync.
 */
@Singleton
class LyricsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lyricsHelper: LyricsHelper,
    private val databaseDao: DatabaseDao,
    private val lyricsPreloadManager: LyricsPreloadManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── Internal State ──────────────────────────────────────────────
    private var currentSong: Song? = null
    private var syncedLyrics: SyncedLyrics? = null
    private var player: ExoPlayer? = null
    private var positionUpdateJob: Job? = null

    // ── Public Flows ─────────────────────────────────────────────────
    /** Current lyric line — all UI components observe this */
    private val _currentEntry = MutableStateFlow<LyricsEntry?>(null)
    val currentEntry: StateFlow<LyricsEntry?> = _currentEntry.asStateFlow()

    /** Complete playback state for rich UI */
    private val _playbackState = MutableStateFlow(LyricsPlaybackState())
    val playbackState: StateFlow<LyricsPlaybackState> = _playbackState.asStateFlow()

    /** Lyrics loading state */
    private val _loadState = MutableStateFlow<LyricsLoadState>(LyricsLoadState.Idle)
    val loadState: StateFlow<LyricsLoadState> = _loadState.asStateFlow()

    /** Full synced lyrics (for full player list) */
    private val _syncedLyrics = MutableStateFlow<SyncedLyrics?>(null)
    val syncedLyricsFlow: StateFlow<SyncedLyrics?> = _syncedLyrics.asStateFlow()

    /** Seek event — emitted when user taps a lyric to seek */
    private val _seekRequest = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val seekRequest: SharedFlow<Long> = _seekRequest.asSharedFlow()

    /** Current playback position (for overlays/notifications) */
    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    /** Is playing state (for auto-hide overlay) */
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // ── Settings ─────────────────────────────────────────────────────
    private val _showTranslation = MutableStateFlow(false)
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()

    private val _showRomanized = MutableStateFlow(false)
    val showRomanized: StateFlow<Boolean> = _showRomanized.asStateFlow()

    // ── Player Listener ──────────────────────────────────────────────
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // Immediate update on seek
            updateCurrentLine(newPosition.positionMs)
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            // Song changed — lyrics will be loaded via loadLyricsForSong()
        }
    }

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Attach an ExoPlayer instance to receive position updates.
     */
    fun attachPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
        exoPlayer.addListener(playerListener)
        _isPlaying.value = exoPlayer.isPlaying
        if (exoPlayer.isPlaying) {
            startPositionUpdates()
        }
    }

    /**
     * Detach the current player.
     */
    fun detachPlayer() {
        stopPositionUpdates()
        player?.removeListener(playerListener)
        player = null
    }

    /**
     * Load lyrics for a song. Called when playback starts or song changes.
     */
    fun loadLyricsForSong(song: Song) {
        currentSong = song
        _loadState.value = LyricsLoadState.Loading

        scope.launch {
            try {
                // Check database first (cached lyrics)
                val cached = databaseDao.getLyrics(song.id)
                if (cached != null && cached.lyrics.isNotBlank()) {
                    val parsed = parseLyricsFromEntity(cached)
                    if (parsed.entries.isNotEmpty()) {
                        syncedLyrics = parsed
                        _syncedLyrics.value = parsed
                        _loadState.value = LyricsLoadState.Success(parsed)
                        Timber.d("Lyrics loaded from cache for: ${song.title}")
                        return@launch
                    }
                }

                // Fetch from providers via LyricsHelper
                val lyricsResult = lyricsHelper.getLyrics(song)
                if (lyricsResult != null && lyricsResult.entries.isNotEmpty()) {
                    syncedLyrics = lyricsResult
                    _syncedLyrics.value = lyricsResult
                    _loadState.value = LyricsLoadState.Success(lyricsResult)

                    // Cache to database
                    cacheLyrics(song.id, lyricsResult)
                    Timber.d("Lyrics fetched and cached for: ${song.title}")
                } else {
                    _loadState.value = LyricsLoadState.NotFound
                    Timber.d("No lyrics found for: ${song.title}")
                }
            } catch (e: Exception) {
                _loadState.value = LyricsLoadState.Error(e.message ?: "Unknown error")
                Timber.e(e, "Failed to load lyrics for: ${song.title}")
            }
        }
    }

    /**
     * Seek to a specific lyric line's start time.
     */
    fun seekToEntry(entry: LyricsEntry) {
        _seekRequest.tryEmit(entry.time)
        player?.seekTo(entry.time)
    }

    /**
     * Toggle translation display.
     */
    fun toggleTranslation() {
        _showTranslation.value = !_showTranslation.value
        updatePlaybackState()
    }

    /**
     * Toggle romanized display.
     */
    fun toggleRomanized() {
        _showRomanized.value = !_showRomanized.value
        updatePlaybackState()
    }

    /**
     * Clear current lyrics.
     */
    fun clear() {
        stopPositionUpdates()
        syncedLyrics = null
        currentSong = null
        _syncedLyrics.value = null
        _currentEntry.value = null
        _playbackState.value = LyricsPlaybackState()
        _loadState.value = LyricsLoadState.Idle
        _currentPositionMs.value = 0L
    }

    // ── Private ──────────────────────────────────────────────────────

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = scope.launch {
            while (isActive) {
                val position = player?.currentPosition ?: 0L
                _currentPositionMs.value = position
                updateCurrentLine(position)
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updateCurrentLine(positionMs: Long) {
        val lyrics = syncedLyrics ?: return
        val newEntry = lyrics.findEntryAt(positionMs)
        val current = _currentEntry.value

        // Only emit when line changes to avoid unnecessary recompositions
        if (newEntry != current) {
            _currentEntry.value = newEntry
            updatePlaybackState()
        } else if (newEntry != null) {
            // Same line, update progress
            updatePlaybackState()
        }
    }

    private fun updatePlaybackState() {
        val lyrics = syncedLyrics ?: return
        val positionMs = player?.currentPosition ?: 0L
        val entry = lyrics.findEntryAt(positionMs)
        val index = lyrics.findEntryIndexAt(positionMs)

        _playbackState.value = LyricsPlaybackState(
            currentEntry = entry,
            nextEntry = entry?.let { lyrics.getNextEntry(it) },
            previousEntry = entry?.let { lyrics.getPreviousEntry(it) },
            currentIndex = index,
            progress = entry?.getProgress(positionMs, lyrics.getNextEntry(entry)) ?: 0f,
            isPlaying = _isPlaying.value,
            showTranslation = _showTranslation.value,
            showRomanized = _showRomanized.value
        )
    }

    private fun parseLyricsFromEntity(entity: LyricsEntity): SyncedLyrics {
        // Parse the stored LRC text into SyncedLyrics
        val entries = LyricsUtils.parseLrc(entity.lyrics)
        return SyncedLyrics(
            entries = entries,
            title = entity.songId, // Or fetch from song metadata
            source = entity.source ?: "cached"
        )
    }

    private suspend fun cacheLyrics(songId: String, lyrics: SyncedLyrics) {
        val lrcText = LyricsUtils.entriesToLrc(lyrics.entries)
        databaseDao.upsert(
            LyricsEntity(
                songId = songId,
                lyrics = lrcText,
                source = lyrics.source
            )
        )
    }

    companion object {
        private const val POSITION_UPDATE_INTERVAL_MS = 100L // 100ms for smooth sync
    }
}
