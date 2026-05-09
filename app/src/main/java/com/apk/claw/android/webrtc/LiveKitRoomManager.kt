package com.apk.claw.android.webrtc

import android.content.Context
import android.util.Log
import com.apk.claw.android.ClawApplication
import com.apk.claw.android.utils.KVUtils
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.ParticipantEvent
import io.livekit.android.events.RoomEvent
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * LiveKit WebRTC room manager singleton.
 * Connects to a LiveKit server and manages the remote digital avatar video track.
 */
object LiveKitRoomManager {

    private const val TAG = "LiveKitRoomManager"

    private var room: io.livekit.android.room.Room? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _avatarStatus = MutableStateFlow(AvatarStatus.IDLE)
    val avatarStatus: StateFlow<AvatarStatus> = _avatarStatus

    private var videoRenderer: SurfaceViewRenderer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var eventCollectJob: Job? = null
    private val participantJobs = mutableMapOf<String, Job>()

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    enum class AvatarStatus {
        IDLE, SPEAKING, PROCESSING
    }

    /**
     * Initialize LiveKit SDK. Call once from Application.onCreate().
     */
    fun init(context: Context) {
        try {
            LiveKit.init(context.applicationContext)
            Log.d(TAG, "LiveKitRoomManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init LiveKit", e)
        }
    }

    /**
     * Connect to the LiveKit room using saved config.
     * @return true if connection was initiated
     */
    fun connect(): Boolean {
        val url = KVUtils.getWebRTCUrl().trim()
        val token = KVUtils.getWebRTCToken().trim()

        if (url.isEmpty() || token.isEmpty()) {
            Log.w(TAG, "WebRTC URL or token is empty, skipping connect")
            return false
        }

        if (!KVUtils.isWebRTCEnabled()) {
            Log.d(TAG, "WebRTC is disabled in settings")
            return false
        }

        // Disconnect any existing connection first
        disconnectInternal()

        _connectionState.value = ConnectionState.CONNECTING
        Log.d(TAG, "Connecting to LiveKit: $url")

        scope.launch {
            try {
                val options = RoomOptions(
                    adaptiveStream = true,
                    dynacast = false,
                )

                val newRoom = LiveKit.create(
                    ClawApplication.instance,
                    options,
                    io.livekit.android.LiveKitOverrides(),
                )

                room = newRoom

                // Start collecting room events in a separate coroutine
                eventCollectJob = scope.launch {
                    try {
                        newRoom.events.events.collect { roomEvent ->
                            handleRoomEvent(roomEvent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error collecting room events", e)
                    }
                }

                // Connect (suspend function)
                newRoom.connect(url, token)
                Log.d(TAG, "LiveKit connect() returned successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect LiveKit room", e)
                _connectionState.value = ConnectionState.ERROR
                room = null
            }
        }

        return true
    }

    private fun handleRoomEvent(roomEvent: RoomEvent) {
        when (roomEvent) {
            is RoomEvent.Connected -> {
                Log.d(TAG, "Connected to LiveKit room")
                _connectionState.value = ConnectionState.CONNECTED
                // Attach any existing video tracks from already-connected participants
                attachExistingRenderer()
                // Start observing all existing remote participants
                observeExistingParticipants()
            }
            is RoomEvent.Disconnected -> {
                Log.d(TAG, "Disconnected: reason=${roomEvent.reason}")
                _connectionState.value = ConnectionState.DISCONNECTED
                _avatarStatus.value = AvatarStatus.IDLE
                cleanupParticipantObservers()
                detachRenderer()
            }
            is RoomEvent.FailedToConnect -> {
                Log.e(TAG, "Connect error: ${roomEvent.error}")
                _connectionState.value = ConnectionState.ERROR
            }
            is RoomEvent.ActiveSpeakersChanged -> {
                _avatarStatus.value = if (roomEvent.speakers.isNotEmpty()) {
                    AvatarStatus.SPEAKING
                } else {
                    AvatarStatus.IDLE
                }
            }
            is RoomEvent.Reconnected -> {
                Log.d(TAG, "Reconnected to LiveKit room")
                _connectionState.value = ConnectionState.CONNECTED
            }
            is RoomEvent.Reconnecting -> {
                Log.d(TAG, "Reconnecting to LiveKit room...")
                _connectionState.value = ConnectionState.CONNECTING
            }
            is RoomEvent.ParticipantConnected -> {
                val participant = roomEvent.participant
                if (participant is RemoteParticipant) {
                    Log.d(TAG, "Remote participant connected: ${participant.identity}")
                    observeParticipant(participant)
                }
            }
            is RoomEvent.ParticipantDisconnected -> {
                val participant = roomEvent.participant
                cleanupParticipantObserver(participant.identity?.value ?: "")
                detachRenderer()
            }
            else -> {
                // Ignore other events
            }
        }
    }

    /**
     * Start observing events for all existing remote participants.
     */
    private fun observeExistingParticipants() {
        val currentRoom = room ?: return
        for ((identity, participant) in currentRoom.remoteParticipants) {
            observeParticipant(participant, identity)
        }
    }

    /**
     * Observe a single remote participant for track subscription events.
     */
    private fun observeParticipant(participant: RemoteParticipant, identity: Participant.Identity? = null) {
        val key = identity?.value ?: participant.identity?.value ?: ""
        if (participantJobs.containsKey(key)) return

        val job = scope.launch {
            try {
                participant.events.events.collect { event ->
                    handleParticipantEvent(event)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error observing participant $key", e)
            }
        }
        participantJobs[key] = job
    }

    private fun handleParticipantEvent(event: ParticipantEvent) {
        when (event) {
            is ParticipantEvent.TrackSubscribed -> {
                val track = event.track
                if (track is RemoteVideoTrack) {
                    Log.d(TAG, "Remote video track subscribed: ${event.publication.sid}")
                    attachRemoteVideoTrack(track)
                }
            }
            is ParticipantEvent.TrackUnsubscribed -> {
                val track = event.track
                if (track is RemoteVideoTrack) {
                    Log.d(TAG, "Remote video track unsubscribed")
                    detachRenderer()
                }
            }
            else -> {}
        }
    }

    /**
     * Disconnect from the current room.
     */
    fun disconnect() {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        eventCollectJob?.cancel()
        eventCollectJob = null
        cleanupParticipantObservers()
        scope.launch {
            try {
                room?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting", e)
            }
        }
        room = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _avatarStatus.value = AvatarStatus.IDLE
        detachRenderer()
    }

    private fun cleanupParticipantObservers() {
        participantJobs.values.forEach { it.cancel() }
        participantJobs.clear()
    }

    private fun cleanupParticipantObserver(identity: String) {
        participantJobs.remove(identity)?.cancel()
    }

    /**
     * Bind a SurfaceViewRenderer to display the remote avatar video.
     */
    fun bindRenderer(renderer: SurfaceViewRenderer) {
        videoRenderer = renderer
        attachExistingRenderer()
    }

    /**
     * Unbind and clean up the video renderer.
     */
    fun unbindRenderer() {
        detachRenderer()
    }

    private fun attachExistingRenderer() {
        val renderer = videoRenderer ?: return
        val currentRoom = room ?: return
        try {
            // Check all remote participants for any video track
            for ((_identity, participant) in currentRoom.remoteParticipants) {
                for ((_sid, pub) in participant.trackPublications) {
                    val track = pub.track
                    if (track is RemoteVideoTrack) {
                        track.addRenderer(renderer)
                        Log.d(TAG, "Attached existing remote video track to renderer")
                        return
                    }
                }
            }
            Log.d(TAG, "No existing remote video track found to attach")
        } catch (e: Exception) {
            Log.w(TAG, "Error attaching existing track", e)
        }
    }

    private fun attachRemoteVideoTrack(track: RemoteVideoTrack) {
        val renderer = videoRenderer ?: return
        try {
            track.addRenderer(renderer)
            _avatarStatus.value = AvatarStatus.IDLE
            Log.d(TAG, "Remote video track attached to renderer")
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching remote video track", e)
        }
    }

    private fun detachRenderer() {
        videoRenderer = null
    }

    /**
     * Set avatar status (e.g. when AI is speaking vs thinking).
     */
    fun setAvatarStatus(status: AvatarStatus) {
        _avatarStatus.value = status
    }
}
