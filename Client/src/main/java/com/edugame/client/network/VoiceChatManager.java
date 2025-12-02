package com.edugame.client.network;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VoiceChatManager - Quản lý voice chat UDP cho phòng chờ
 *
 * Features:
 * - Record audio từ microphone
 * - Send audio qua UDP đến server
 * - Receive audio từ server và play
 * - Auto-detect microphone
 * - Echo cancellation support
 */
public class VoiceChatManager {

    // ==================== Constants ====================
    private static final int SAMPLE_RATE = 16000; // 16kHz
    private float volumeGain = 3.0f;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1; // Mono
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    private static final int BUFFER_SIZE = 1024; // Bytes per packet
    private static final int UDP_PORT = 9999; // Port cho voice chat

    // ==================== Fields ====================
    private DatagramSocket udpSocket;
    private InetAddress serverAddress;
    private int serverPort;
    private String roomId;
    private int userId;

    private TargetDataLine microphone;
    private SourceDataLine speakers;

    private Thread sendThread;
    private Thread receiveThread;

    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private AtomicBoolean isPlaying = new AtomicBoolean(false);
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    private AudioFormat audioFormat;
    private VoiceStatusListener statusListener;

    // ==================== Constructor ====================

    /**
     * Khởi tạo VoiceChatManager
     * @param serverHost Server hostname
     * @param roomId Room ID
     * @param userId User ID
     */
    public VoiceChatManager(String serverHost, String roomId, int userId) {
        try {
            this.serverAddress = InetAddress.getByName(serverHost);
            this.serverPort = UDP_PORT;
            this.roomId = roomId;
            this.userId = userId;

            // Setup audio format
            this.audioFormat = new AudioFormat(
                    SAMPLE_RATE,
                    SAMPLE_SIZE_IN_BITS,
                    CHANNELS,
                    SIGNED,
                    BIG_ENDIAN
            );

            System.out.println("✅ VoiceChatManager initialized");
            System.out.println("   Server: " + serverHost + ":" + serverPort);
            System.out.println("   Room: " + roomId + " | User: " + userId);

        } catch (UnknownHostException e) {
            System.err.println("❌ Invalid server address: " + serverHost);
            e.printStackTrace();
        }
    }

    // ==================== Public Methods ====================

    /**
     * Start voice chat
     */
    public boolean start() {
        if (isRunning.get()) {
            System.out.println("⚠️ Voice chat already running");
            return false;
        }

        try {
            // Create UDP socket
            udpSocket = new DatagramSocket();
            System.out.println("✅ UDP socket created on port: " + udpSocket.getLocalPort());

            // Setup microphone
            if (!setupMicrophone()) {
                System.err.println("❌ Failed to setup microphone");
                return false;
            }

            // Setup speakers
            if (!setupSpeakers()) {
                System.err.println("❌ Failed to setup speakers");
                return false;
            }

            // Send JOIN_VOICE message to server
            sendJoinVoiceMessage();

            // Start recording and sending
            isRunning.set(true);
            startRecording();
            startReceiving();

            if (statusListener != null) {
                statusListener.onVoiceStarted();
            }

            System.out.println("🎤 Voice chat started successfully");
            return true;

        } catch (Exception e) {
            System.err.println("❌ Error starting voice chat: " + e.getMessage());
            e.printStackTrace();
            stop();
            return false;
        }
    }

    /**
     * Stop voice chat
     */
    public void stop() {
        System.out.println("🛑 Stopping voice chat...");

        isRunning.set(false);
        isRecording.set(false);
        isPlaying.set(false);

        // Send LEAVE_VOICE message
        sendLeaveVoiceMessage();

        // Stop threads
        if (sendThread != null) {
            sendThread.interrupt();
        }
        if (receiveThread != null) {
            receiveThread.interrupt();
        }

        // Close audio lines
        if (microphone != null && microphone.isOpen()) {
            microphone.stop();
            microphone.close();
            System.out.println("   🎤 Microphone closed");
        }

        if (speakers != null && speakers.isOpen()) {
            speakers.stop();
            speakers.drain();
            speakers.close();
            System.out.println("   🔊 Speakers closed");
        }

        // Close UDP socket
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
            System.out.println("   📡 UDP socket closed");
        }

        if (statusListener != null) {
            statusListener.onVoiceStopped();
        }

        System.out.println("✅ Voice chat stopped");
    }

    /**
     * Check if voice chat is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Set status listener
     */
    public void setStatusListener(VoiceStatusListener listener) {
        this.statusListener = listener;
    }

    /**
     * Mute/unmute microphone
     */
    public void setMuted(boolean muted) {
        isRecording.set(!muted);

        if (muted && microphone != null && microphone.isOpen()) {
            microphone.stop();
        } else if (!muted && microphone != null && microphone.isOpen()) {
            microphone.start();
        }

        System.out.println((muted ? "🔇" : "🎤") + " Microphone " + (muted ? "muted" : "unmuted"));
    }

    // ==================== Private Methods ====================

    /**
     * Setup microphone
     */
    private boolean setupMicrophone() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("❌ Microphone not supported");
                return false;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(audioFormat, BUFFER_SIZE * 4);

            System.out.println("✅ Microphone setup complete");
            return true;

        } catch (LineUnavailableException e) {
            System.err.println("❌ Microphone unavailable: " + e.getMessage());
            return false;
        }
    }

    /**
     * Setup speakers
     */
    private boolean setupSpeakers() {
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("❌ Speakers not supported");
                return false;
            }

            speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(audioFormat, BUFFER_SIZE * 4);
            speakers.start();

            System.out.println("✅ Speakers setup complete");
            return true;

        } catch (LineUnavailableException e) {
            System.err.println("❌ Speakers unavailable: " + e.getMessage());
            return false;
        }
    }

    /**
     * Start recording and sending audio
     */
    private void startRecording() {
        isRecording.set(true);
        microphone.start();

        sendThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];

            System.out.println("🎤 Recording thread started");

            while (isRunning.get()) {
                try {
                    if (isRecording.get()) {
                        int bytesRead = microphone.read(buffer, 0, buffer.length);

                        if (bytesRead > 0) {
                            sendAudioPacket(buffer, bytesRead);
                        }
                    } else {
                        Thread.sleep(100);
                    }

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    if (isRunning.get()) {
                        System.err.println("❌ Error recording: " + e.getMessage());
                    }
                }
            }

            System.out.println("🛑 Recording thread stopped");
        }, "VoiceChat-Send");

        sendThread.start();
    }

    /**
     * Start receiving and playing audio
     */
    private void startReceiving() {
        isPlaying.set(true);

        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE + 100]; // Extra space for metadata
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            System.out.println("🔊 Receiving thread started");

            while (isRunning.get()) {
                try {
                    udpSocket.receive(packet);

                    if (isPlaying.get()) {
                        processAudioPacket(packet);
                    }

                } catch (IOException e) {
                    if (isRunning.get()) {
                        System.err.println("❌ Error receiving: " + e.getMessage());
                    }
                }
            }

            System.out.println("🛑 Receiving thread stopped");
        }, "VoiceChat-Receive");

        receiveThread.start();
    }

    /**
     * Send audio packet to server
     */
    private void sendAudioPacket(byte[] audioData, int length) {
        try {
            // Create packet with metadata
            // Format: [userId(4)] [roomId length(4)] [roomId] [audio data]

            byte[] roomIdBytes = roomId.getBytes();
            int totalLength = 4 + 4 + roomIdBytes.length + length;
            byte[] packetData = new byte[totalLength];

            // Write userId (4 bytes)
            packetData[0] = (byte) (userId >> 24);
            packetData[1] = (byte) (userId >> 16);
            packetData[2] = (byte) (userId >> 8);
            packetData[3] = (byte) userId;

            // Write roomId length (4 bytes)
            int offset = 4;
            packetData[offset++] = (byte) (roomIdBytes.length >> 24);
            packetData[offset++] = (byte) (roomIdBytes.length >> 16);
            packetData[offset++] = (byte) (roomIdBytes.length >> 8);
            packetData[offset++] = (byte) roomIdBytes.length;

            // Write roomId
            System.arraycopy(roomIdBytes, 0, packetData, offset, roomIdBytes.length);
            offset += roomIdBytes.length;

            // Write audio data
            System.arraycopy(audioData, 0, packetData, offset, length);

            // Send packet
            DatagramPacket packet = new DatagramPacket(
                    packetData,
                    totalLength,
                    serverAddress,
                    serverPort
            );

            udpSocket.send(packet);

        } catch (IOException e) {
            System.err.println("❌ Error sending audio: " + e.getMessage());
        }
    }

    /**
     * Process received audio packet
     */
    private void processAudioPacket(DatagramPacket packet) {
        try {
            byte[] data = packet.getData();
            int length = packet.getLength();

            // Parse metadata
            int senderId = ((data[0] & 0xFF) << 24) |
                    ((data[1] & 0xFF) << 16) |
                    ((data[2] & 0xFF) << 8) |
                    (data[3] & 0xFF);

            // Don't play own audio (echo prevention)
            if (senderId == userId) {
                return;
            }

            int offset = 4;
            int roomIdLength = ((data[offset++] & 0xFF) << 24) |
                    ((data[offset++] & 0xFF) << 16) |
                    ((data[offset++] & 0xFF) << 8) |
                    (data[offset++] & 0xFF);

            offset += roomIdLength; // Skip roomId

            // Play audio data
            int audioLength = length - offset;
            if (audioLength > 0 && speakers != null && speakers.isOpen()) {
//                speakers.write(data, offset, audioLength);
                // Amplify audio before playing
                byte[] amplified = amplifyAudio(data, offset, audioLength);
                speakers.write(amplified, 0, amplified.length);

            }

        } catch (Exception e) {
            System.err.println("❌ Error processing audio: " + e.getMessage());
        }
    }



    /**
     * Send JOIN_VOICE message to server
     */
    private void sendJoinVoiceMessage() {
        try {
            String message = "JOIN_VOICE:" + userId + ":" + roomId;
            byte[] data = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    serverAddress,
                    serverPort
            );

            udpSocket.send(packet);
            System.out.println("📤 Sent JOIN_VOICE to server");

        } catch (IOException e) {
            System.err.println("❌ Error sending JOIN_VOICE: " + e.getMessage());
        }
    }

    /**
     * Send LEAVE_VOICE message to server
     */
    private void sendLeaveVoiceMessage() {
        try {
            if (udpSocket != null && !udpSocket.isClosed()) {
                String message = "LEAVE_VOICE:" + userId + ":" + roomId;
                byte[] data = message.getBytes();

                DatagramPacket packet = new DatagramPacket(
                        data,
                        data.length,
                        serverAddress,
                        serverPort
                );

                udpSocket.send(packet);
                System.out.println("📤 Sent LEAVE_VOICE to server");
            }
        } catch (IOException e) {
            System.err.println("❌ Error sending LEAVE_VOICE: " + e.getMessage());
        }
    }

    public void setVolume(float gain) {
        this.volumeGain = gain;
        System.out.println("🔊 Volume set to: " + gain + "x");
    }

    private byte[] amplifyAudio(byte[] input, int offset, int length) {
        byte[] output = new byte[length];

        for (int i = 0; i < length; i += 2) {
            // PCM 16-bit little-endian
            int low = input[offset + i] & 0xFF;
            int high = input[offset + i + 1];

            short sample = (short) ((high << 8) | low);

            // Multiply volume
            int amplified = (int) (sample * volumeGain);

            // Clamp to 16-bit range
            if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE;
            if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE;

            // Write back
            output[i]     = (byte) (amplified & 0xFF);
            output[i + 1] = (byte) ((amplified >> 8) & 0xFF);
        }

        return output;
    }

    // ==================== Interface ====================

    /**
     * Listener for voice chat status
     */
    public interface VoiceStatusListener {
        void onVoiceStarted();
        void onVoiceStopped();
        void onError(String error);
    }
}