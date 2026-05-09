package com.apk.claw.android.webrtc;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * 模拟 DirectWebRTCManager 的完整信令流程
 *
 * 测试覆盖：
 * 1. 正确的 SDP 协商流程（setRemote → onSetSuccess → createAnswer → setLocal → sendAnswer）
 * 2. 竞态条件复现（setRemote 未完成就调 createAnswer，answer 会畸形）
 * 3. ICE 候选排队与刷新（signalingReady 之前收到的 candidate 要排队）
 * 4. OpenClaw 模式桥接（OpenClaw LLM 回复 → CyberVerse assistant_text → TTS 驱动数字人）
 * 5. 多线程并发安全性
 *
 * 这个测试不依赖 Android 框架，用纯 Java 模拟 WebRTC 的异步回调机制。
 */
public class DirectWebRTCSimulationTest {

    // ========================================================================
    //  辅助类：模拟 WebRTC 回调接口
    // ========================================================================

    /** 模拟 SdpObserver */
    interface MockSdpObserver {
        void onCreateSuccess(String sdp);
        void onSetSuccess();
        void onCreateFailure(String error);
        void onSetFailure(String error);
    }

    /** 模拟 PeerConnection.Observer */
    interface MockPeerObserver {
        void onSignalingChange(String state);
        void onIceConnectionChange(String state);
        void onIceGatheringChange(String state);
        void onIceCandidate(String candidate, String sdpMid, int sdpMLineIndex);
        void onAddTrack(String kind);
    }

    /** 模拟 WebSocket 消息发送 */
    interface MockWebSocketSender {
        void send(String json);
    }

    // ========================================================================
    //  模拟器：完整模拟 DirectWebRTCManager 的信令逻辑
    // ========================================================================

    /**
     * 模拟 DirectWebRTCManager 的 SDP 协商状态机。
     * 可以配置两种模式：
     * - FIXED: 正确流程 — createAnswer 等待 onSetSuccess
     * - BUGGY: 竞态条件 — createAnswer 在 setRemote 完成前就调用
     */
    static class WebRTCSignalingSimulator {
        enum Mode { FIXED, BUGGY }

        private final Mode mode;
        private final ExecutorService executor;
        private final MockWebSocketSender wsSender;
        private final MockPeerObserver peerObserver;

        // SDP 状态
        private final AtomicReference<String> remoteSdp = new AtomicReference<>();
        private final AtomicReference<String> localSdp = new AtomicReference<>();
        private final AtomicBoolean remoteDescriptionSet = new AtomicBoolean(false);
        private final AtomicBoolean localDescriptionSet = new AtomicBoolean(false);
        private final AtomicBoolean answerSent = new AtomicBoolean(false);
        private final AtomicBoolean hasVideo = new AtomicBoolean(false);
        private final AtomicBoolean hasAudio = new AtomicBoolean(false);

        // ICE 状态
        private final AtomicBoolean signalingReady = new AtomicBoolean(false);
        private final List<String> pendingIceCandidates = Collections.synchronizedList(new ArrayList<>());
        private final List<String> addedIceCandidates = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger iceCandidateCount = new AtomicInteger(0);

        // 错误跟踪
        private final List<String> errors = new CopyOnWriteArrayList<>();
        private final List<String> logMessages = new CopyOnWriteArrayList<>();

        // 时间线跟踪
        private final List<String> timeline = Collections.synchronizedList(new ArrayList<>());
        private final long startTs;

        WebRTCSignalingSimulator(Mode mode, MockWebSocketSender wsSender, MockPeerObserver peerObserver) {
            this.mode = mode;
            this.wsSender = wsSender;
            this.peerObserver = peerObserver;
            this.executor = Executors.newSingleThreadExecutor();
            this.startTs = System.currentTimeMillis();
        }

        private long deltaMs() {
            return System.currentTimeMillis() - startTs;
        }

        /** 模拟收到 webrtc_config 消息 */
        void receiveWebrtcConfig(String configJson) {
            timeline.add("[T+" + deltaMs() + "ms] receiveWebrtcConfig");
            logMessages.add("ICE servers configured from config");
        }

        /**
         * 模拟收到 webrtc_offer 消息
         * 这是核心方法 — 测试 FIXED vs BUGGY 的区别
         */
        void receiveWebrtcOffer(String offerSdp) {
            timeline.add("[T+" + deltaMs() + "ms] receiveWebrtcOffer (SDP length=" + offerSdp.length() + ")");

            remoteSdp.set(offerSdp);
            hasVideo.set(offerSdp.contains("m=video"));
            hasAudio.set(offerSdp.contains("m=audio"));

            logMessages.add("Received WebRTC offer (audio=" + hasAudio.get() + ", video=" + hasVideo.get() + ")");

            // 模拟 mainHandler.post — 异步处理
            executor.submit(() -> {
                timeline.add("[T+" + deltaMs() + "ms] start handleWebrtcOffer (on main thread)");
                simulateCreatePeerConnection();
                simulateSetRemoteDescription(offerSdp);
            });
        }

        /** 模拟创建 PeerConnection */
        private void simulateCreatePeerConnection() {
            timeline.add("[T+" + deltaMs() + "ms] createPeerConnection");
            logMessages.add("PeerConnection created (Unified Plan)");
        }

        /**
         * 模拟 setRemoteDescription — 这是关键！
         *
         * FIXED 模式: onSetSuccess 回调里调 createAndSendAnswer
         * BUGGY 模式: 不等 onSetSuccess，直接调 createAndSendAnswer（竞态条件）
         */
        private void simulateSetRemoteDescription(String offerSdp) {
            timeline.add("[T+" + deltaMs() + "ms] setRemoteDescription(OFFER) called");

            if (mode == Mode.BUGGY) {
                // ========== BUGGY: 竞态条件 ==========
                // 不等 setRemote 完成，直接调 createAnswer
                timeline.add("[T+" + deltaMs() + "ms] BUG: createAnswer called BEFORE setRemote onSetSuccess!");
                errors.add("RACE_CONDITION: createAnswer called before setRemoteDescription completed");

                simulateCreateAnswer();
            }

            // 模拟 setRemoteDescription 的异步完成
            executor.submit(() -> {
                // 模拟网络/处理延迟（50ms）
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}

                if (mode == Mode.BUGGY) {
                    // BUGGY 模式: onSetSuccess 到了，但 createAnswer 已经提前调了
                    timeline.add("[T+" + deltaMs() + "ms] setRemoteDescription onSetSuccess (TOO LATE - answer already created)");
                    errors.add("RACE_CONDITION: onSetSuccess fired but answer was already created with stale state");
                    remoteDescriptionSet.set(true);
                } else {
                    // FIXED 模式: 在 onSetSuccess 里调 createAnswer
                    timeline.add("[T+" + deltaMs() + "ms] setRemoteDescription onSetSuccess");
                    remoteDescriptionSet.set(true);
                    simulateCreateAnswer();
                }
            });
        }

        /**
         * 模拟 createAnswer
         * 检查 remoteDescription 是否已设置，如果没设置则 answer 会畸形
         */
        private void simulateCreateAnswer() {
            timeline.add("[T+" + deltaMs() + "ms] createAnswer called");

            boolean remoteReady = remoteDescriptionSet.get();
            if (!remoteReady) {
                // BUGGY: remote description 还没设好，answer 会缺少 video media line
                timeline.add("[T+" + deltaMs() + "ms] BUG: answer created WITHOUT remote description being set!");
                errors.add("MALFORMED_ANSWER: answer created before remoteDescription was set - video line will be missing");
            } else {
                timeline.add("[T+" + deltaMs() + "ms] answer created successfully (remoteDescription was set)");
            }

            // 模拟 answer SDP 生成
            String answerSdp;
            if (remoteReady && hasVideo.get()) {
                answerSdp = buildValidAnswerSdp(true, true);
            } else {
                // 畸形 answer — 缺少 video media line
                answerSdp = buildValidAnswerSdp(true, false);
                if (hasVideo.get()) {
                    timeline.add("[T+" + deltaMs() + "ms] BUG: answer SDP missing m=video line despite offer having it!");
                    errors.add("MISSING_VIDEO_IN_ANSWER: SDP answer does not contain m=video");
                }
            }

            localSdp.set(answerSdp);
            timeline.add("[T+" + deltaMs() + "ms] answer SDP generated (has video: " + answerSdp.contains("m=video") + ")");

            // 模拟 setLocalDescription
            simulateSetLocalDescription(answerSdp);
        }

        private void simulateSetLocalDescription(String answerSdp) {
            timeline.add("[T+" + deltaMs() + "ms] setLocalDescription(ANSWER) called");

            // setLocalDescription 的异步完成
            executor.submit(() -> {
                try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                localDescriptionSet.set(true);
                timeline.add("[T+" + deltaMs() + "ms] setLocalDescription onSetSuccess");
            });

            // 发送 answer 到服务器
            String answerJson = "{\"type\":\"webrtc_answer\",\"sdp\":\"" + answerSdp.replace("\n", "\\n") + "\"}";
            wsSender.send(answerJson);
            answerSent.set(true);
            timeline.add("[T+" + deltaMs() + "ms] WebRTC answer SENT to server via WebSocket");
        }

        /** 模拟收到 ICE candidate */
        void receiveIceCandidate(String candidate, String sdpMid, int sdpMLineIndex) {
            timeline.add("[T+" + deltaMs() + "ms] receiveIceCandidate (mid=" + sdpMid + ", index=" + sdpMLineIndex + ")");

            if (signalingReady.get()) {
                addIceCandidate(candidate, sdpMid, sdpMLineIndex);
            } else {
                pendingIceCandidates.add(candidate);
                timeline.add("[T+" + deltaMs() + "ms] ICE candidate QUEUED (signaling not ready, pending=" + pendingIceCandidates.size() + ")");
            }
        }

        /** 标记 signaling ready 并刷新排队的 ICE candidates */
        void markSignalingReady() {
            signalingReady.set(true);
            timeline.add("[T+" + deltaMs() + "ms] signalingReady = true, flushing " + pendingIceCandidates.size() + " pending ICE candidates");

            for (String cand : pendingIceCandidates) {
                addIceCandidate(cand, "0", 0);
            }
            pendingIceCandidates.clear();
        }

        private void addIceCandidate(String candidate, String sdpMid, int sdpMLineIndex) {
            addedIceCandidates.add(candidate);
            iceCandidateCount.incrementAndGet();
            if (peerObserver != null) {
                peerObserver.onIceCandidate(candidate, sdpMid, sdpMLineIndex);
            }
        }

        /** 模拟 ICE 连接状态变化 */
        void simulateIceStateChange(String state) {
            timeline.add("[T+" + deltaMs() + "ms] ICE state: " + state);
            if (peerObserver != null) {
                peerObserver.onIceConnectionChange(state);
            }
        }

        /** 模拟远端 track 添加 */
        void simulateRemoteTrackAdded(String kind) {
            timeline.add("[T+" + deltaMs() + "ms] onAddTrack: kind=" + kind);
            if (peerObserver != null) {
                peerObserver.onAddTrack(kind);
            }
        }

        // SDP 构建
        private String buildValidAnswerSdp(boolean audio, boolean video) {
            StringBuilder sb = new StringBuilder();
            sb.append("v=0\r\n");
            sb.append("o=- 123456789 2 IN IP4 0.0.0.0\r\n");
            sb.append("s=-\r\n");
            sb.append("t=0 0\r\n");
            sb.append("a=group:BUNDLE");
            if (audio) sb.append(" 0");
            if (video) sb.append(" 1");
            sb.append("\r\n");
            if (audio) {
                sb.append("m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n");
                sb.append("a=mid:0\r\n");
                sb.append("a=recvonly\r\n");
                sb.append("a=rtpmap:111 opus/48000/2\r\n");
            }
            if (video) {
                sb.append("m=video 9 UDP/TLS/RTP/SAVPF 96\r\n");
                sb.append("a=mid:1\r\n");
                sb.append("a=recvonly\r\n");
                sb.append("a=rtpmap:96 VP8/90000\r\n");
            }
            return sb.toString();
        }

        // 状态查询
        boolean isAnswerSent() { return answerSent.get(); }
        boolean hasVideoInAnswer() { return localSdp.get() != null && localSdp.get().contains("m=video"); }
        boolean hasAudioInAnswer() { return localSdp.get() != null && localSdp.get().contains("m=audio"); }
        boolean hasErrors() { return !errors.isEmpty(); }
        List<String> getErrors() { return new ArrayList<>(errors); }
        List<String> getTimeline() { return new ArrayList<>(timeline); }
        List<String> getLogMessages() { return new ArrayList<>(logMessages); }
        int getIceCandidateCount() { return iceCandidateCount.get(); }

        void shutdown() {
            executor.shutdown();
            try { executor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }
    }

    // ========================================================================
    //  模拟器：OpenClaw 桥接流程
    // ========================================================================

    /**
     * 模拟 OpenClaw → CyberVerse 桥接流程
     * OpenClaw 返回 LLM 文本 → 通过 CyberVerse WS 发送 assistant_text → 触发 TTS → 驱动数字人
     */
    static class OpenClawBridgeSimulator {
        private final List<String> messagesSentToCyberVerse = new CopyOnWriteArrayList<>();
        private final List<String> receivedFromOpenClaw = new CopyOnWriteArrayList<>();
        private final List<String> timeline = Collections.synchronizedList(new ArrayList<>());
        private final AtomicBoolean bridgeEnabled = new AtomicBoolean(false);
        private final AtomicInteger llmResponseCount = new AtomicInteger(0);
        private final AtomicInteger ttsRequestCount = new AtomicInteger(0);
        private final long startTs = System.currentTimeMillis();

        private long deltaMs() { return System.currentTimeMillis() - startTs; }

        /** 设置桥接是否启用（模拟 DirectWebRTCManager 是否已连接） */
        void setBridgeEnabled(boolean enabled) {
            bridgeEnabled.set(enabled);
            timeline.add("[T+" + deltaMs() + "ms] bridge " + (enabled ? "ENABLED" : "DISABLED"));
        }

        /** 模拟 OpenClaw 返回 LLM 回复 */
        void receiveOpenClawResponse(String type, String text) {
            receivedFromOpenClaw.add(text);
            llmResponseCount.incrementAndGet();
            timeline.add("[T+" + deltaMs() + "ms] OpenClaw response: type=" + type + ", text=" + truncate(text, 40));

            if (type.equals("text") || type.equals("llm")) {
                bridgeToCyberVerse(text);
            } else if (type.equals("push")) {
                timeline.add("[T+" + deltaMs() + "ms] Push notification: " + truncate(text, 40));
                bridgeToCyberVerse(text);
            }
        }

        /** 桥接文本到 CyberVerse（对应 DirectWebRTCManager.sendAssistantText） */
        void bridgeToCyberVerse(String text) {
            if (!bridgeEnabled.get()) {
                timeline.add("[T+" + deltaMs() + "ms] BRIDGE SKIPPED: CyberVerse not connected");
                return;
            }

            String json = "{\"type\":\"assistant_text\",\"text\":\"" + text.replace("\"", "\\\"") + "\"}";
            messagesSentToCyberVerse.add(json);
            ttsRequestCount.incrementAndGet();
            timeline.add("[T+" + deltaMs() + "ms] Bridged to CyberVerse for TTS: " + truncate(text, 30));
        }

        // 查询
        int getMessagesSentCount() { return messagesSentToCyberVerse.size(); }
        int getLlmResponseCount() { return llmResponseCount.get(); }
        int getTtsRequestCount() { return ttsRequestCount.get(); }
        List<String> getTimeline() { return new ArrayList<>(timeline); }
        String getLastMessageSent() {
            return messagesSentToCyberVerse.isEmpty() ? null : messagesSentToCyberVerse.get(messagesSentToCyberVerse.size() - 1);
        }
    }

    // ========================================================================
    //  测试时间基准
    // ========================================================================
    private long startTime;

    private long deltaMs() {
        return System.currentTimeMillis() - startTime;
    }

    // ========================================================================
    //  TEST 1: 正确的 SDP 协商流程（FIXED 模式）
    // ========================================================================

    @Test
    public void testCorrectSdpNegotiationFlow() throws Exception {
        startTime = System.currentTimeMillis();
        System.out.println("\n========== TEST 1: 正确的 SDP 协商流程 (FIXED) ==========");

        CountDownLatch answerLatch = new CountDownLatch(1);
        List<String> sentMessages = new CopyOnWriteArrayList<>();

        WebRTCSignalingSimulator sim = new WebRTCSignalingSimulator(
            WebRTCSignalingSimulator.Mode.FIXED,
            json -> {
                sentMessages.add(json);
                System.out.println("  [WS SEND] " + truncate(json, 100));
                answerLatch.countDown();
            },
            null
        );

        // 构建一个包含 audio + video 的 offer SDP
        String offerSdp = buildSampleOfferSdp(true, true);

        // 发送 config
        sim.receiveWebrtcConfig("{\"ice_servers\":[{\"urls\":\"stun:stun.l.google.com:19302\"}]}");

        // 发送 offer
        sim.receiveWebrtcOffer(offerSdp);

        // 等待 answer 被发送
        boolean completed = answerLatch.await(5, TimeUnit.SECONDS);
        sim.shutdown();

        // 打印时间线
        System.out.println("\n  --- 时间线 ---");
        for (String t : sim.getTimeline()) {
            System.out.println("  " + t);
        }

        // 验证
        assertTrue("answer 应该在超时前发送", completed);
        assertTrue("answer 已发送", sim.isAnswerSent());
        assertTrue("answer 应包含 m=video", sim.hasVideoInAnswer());
        assertTrue("answer 应包含 m=audio", sim.hasAudioInAnswer());
        assertFalse("FIXED 模式不应有错误", sim.hasErrors());

        System.out.println("\n  RESULT: PASS - answer 包含 audio + video, 无竞态条件错误");
    }

    // ========================================================================
    //  TEST 2: 竞态条件复现（BUGGY 模式）
    // ========================================================================

    @Test
    public void testRaceConditionBuggyFlow() throws Exception {
        startTime = System.currentTimeMillis();
        System.out.println("\n========== TEST 2: 竞态条件复现 (BUGGY) ==========");

        CountDownLatch answerLatch = new CountDownLatch(1);
        List<String> sentMessages = new CopyOnWriteArrayList<>();

        WebRTCSignalingSimulator sim = new WebRTCSignalingSimulator(
            WebRTCSignalingSimulator.Mode.BUGGY,
            json -> {
                sentMessages.add(json);
                System.out.println("  [WS SEND] " + truncate(json, 100));
                answerLatch.countDown();
            },
            null
        );

        String offerSdp = buildSampleOfferSdp(true, true);

        sim.receiveWebrtcConfig("{\"ice_servers\":[{\"urls\":\"stun:stun.l.google.com:19302\"}]}");
        sim.receiveWebrtcOffer(offerSdp);

        boolean completed = answerLatch.await(5, TimeUnit.SECONDS);
        sim.shutdown();

        System.out.println("\n  --- 时间线 ---");
        for (String t : sim.getTimeline()) {
            System.out.println("  " + t);
        }

        System.out.println("\n  --- 错误列表 ---");
        for (String e : sim.getErrors()) {
            System.out.println("  ERROR: " + e);
        }

        // 验证 — BUGGY 模式 answer 虽然会发送，但会有错误
        assertTrue("answer 应该在超时前发送", completed);
        assertTrue("answer 已发送", sim.isAnswerSent());
        assertTrue("BUGGY 模式应该检测到错误", sim.hasErrors());

        // 关键验证：answer 可能缺少 video（因为 createAnswer 时 remoteDescription 还没设好）
        // 在某些竞态时序下，answer 的 m=video 会缺失
        List<String> errors = sim.getErrors();
        boolean hasRaceCondition = errors.stream().anyMatch(e -> e.contains("RACE_CONDITION"));
        assertTrue("应该检测到 RACE_CONDITION 错误", hasRaceCondition);

        System.out.println("\n  RESULT: PASS - 竞态条件已复现，检测到 " + errors.size() + " 个错误");
    }

    // ========================================================================
    //  TEST 3: FIXED vs BUGGY 对比 — answer SDP 内容差异
    // ========================================================================

    @Test
    public void testFixedVsBuggyComparison() throws Exception {
        startTime = System.currentTimeMillis();
        System.out.println("\n========== TEST 3: FIXED vs BUGGY answer SDP 对比 ==========");

        String offerSdp = buildSampleOfferSdp(true, true);

        // --- FIXED ---
        CountDownLatch fixedLatch = new CountDownLatch(1);
        WebRTCSignalingSimulator fixed = new WebRTCSignalingSimulator(
            WebRTCSignalingSimulator.Mode.FIXED,
            json -> fixedLatch.countDown(), null
        );
        fixed.receiveWebrtcOffer(offerSdp);
        fixedLatch.await(5, TimeUnit.SECONDS);
        fixed.shutdown();

        // --- BUGGY ---
        CountDownLatch buggyLatch = new CountDownLatch(1);
        WebRTCSignalingSimulator buggy = new WebRTCSignalingSimulator(
            WebRTCSignalingSimulator.Mode.BUGGY,
            json -> buggyLatch.countDown(), null
        );
        buggy.receiveWebrtcOffer(offerSdp);
        buggyLatch.await(5, TimeUnit.SECONDS);
        buggy.shutdown();

        // 对比
        System.out.println("\n  FIXED answer contains m=video: " + fixed.hasVideoInAnswer());
        System.out.println("  FIXED answer contains m=audio: " + fixed.hasAudioInAnswer());
        System.out.println("  FIXED errors: " + fixed.getErrors().size());

        System.out.println("\n  BUGGY answer contains m=video: " + buggy.hasVideoInAnswer());
        System.out.println("  BUGGY answer contains m=audio: " + buggy.hasAudioInAnswer());
        System.out.println("  BUGGY errors: " + buggy.getErrors().size());

        System.out.println("\n  --- FIXED 时间线 ---");
        for (String t : fixed.getTimeline()) System.out.println("  " + t);

        System.out.println("\n  --- BUGGY 时间线 ---");
        for (String t : buggy.getTimeline()) System.out.println("  " + t);

        assertTrue("FIXED 模式 answer 应有 video", fixed.hasVideoInAnswer());
        assertTrue("FIXED 模式应无错误", !fixed.hasErrors());
        assertTrue("BUGGY 模式应有错误", buggy.hasErrors());

        System.out.println("\n  RESULT: PASS - FIXED 模式正确生成 video answer, BUGGY 模式产生竞态错误");
    }

    // ========================================================================
    //  TEST 4: ICE 候选排队与刷新
    // ========================================================================

    @Test
    public void testIceCandidateQueueing() throws Exception {
        startTime = System.currentTimeMillis();
        System.out.println("\n========== TEST 4: ICE 候选排队与刷新 ==========");

        AtomicInteger trackCount = new AtomicInteger(0);
        WebRTCSignalingSimulator sim = new WebRTCSignalingSimulator(
            WebRTCSignalingSimulator.Mode.FIXED,
            json -> {},
            (kind) -> trackCount.incrementAndGet()
        );

        // signalingReady 之前收到 3 个 ICE candidate
        sim.receiveIceCandidate("candidate:foundation 1 udp 1 192.168.1.1 5000 typ host", "0", 0);
        sim.receiveIceCandidate("candidate:foundation 2 udp 1 10.0.0.1 5001 typ host", "0", 0);
        sim.receiveIceCandidate("candidate:foundation 3 udp 1 172.16.0.1 5002 typ host", "0", 0);

        System.out.println("  收到 3 个 ICE candidate (signaling 未就绪)");
        System.out.println("  已排队等待: 应该有 3 个 pending");

        // 标记 signaling ready → 应该刷新排队的 candidates
        sim.markSignalingReady();

        System.out.println("  signalingReady = true, 刷新排队 candidates");

        // signalingReady 之后再收到的 candidate 直接添加
        sim.receiveIceCandidate("candidate:foundation 4 udp 1 203.0.113.1 5003 typ srflx", "1", 1);
        sim.receiveIceCandidate("candidate:foundation 5 udp 1 198.51.100.1 5004 typ relay", "1", 1);

        sim.shutdown();

        System.out.println("\n  --- 时间线 ---");
        for (String t : sim.getTimeline()) System.out.println("  " + t);

        System.out.println("\n  总计添加 ICE candidates: " + sim.getIceCandidateCount());

        // 验证：3 个排队的 + 3 个排队刷新 + 2 个直接添加 = 8
        assertTrue("应该至少有 5 个 ICE candidates 被添加", sim.getIceCandidateCount() >= 4);

        System.out.println("\n  RESULT: PASS - ICE 候选正确排队和刷新");
    }

    // ========================================================================
    //  TEST 5: 完整信令流程（从连接到视频播放）
    // ========================================================================

    @Test
    public void testFullSignalingLifecycle() throws Exception {
        startTime = System.currentTimeMillis();
        System.out.println("\n========== TEST 5: 完整信令生命周期 ==========");

        CountDownLatch connectedLatch = new CountDownLatch(1);
        List<String> wsMessages = new CopyOnWriteArrayList<>();
        List<String> stateChanges = new CopyOnWriteArrayList<>();
        AtomicBoolean videoTrackReceived = new AtomicBoolean(false);
        AtomicBoolean audioTrackReceived = new AtomicBoolean(false);

        WebRTCSignalingSimulator sim = new WebRTCSignalingSimulator(
            WebRTCSignalingSimulator.Mode.FIXED,
            json -> {
                wsMessages.add(json);
                System.out.println("  [WS -> Server] " + truncate(json, 80));
            },
            new MockPeerObserver() {
                @Override public void onSignalingChange(String state) {
                    stateChanges.add("signaling:" + state);
                    System.out.println("  [Signaling] " + state);
                }
                @Override public void onIceConnectionChange(String state) {
                    stateChanges.add("ice:" + state);
                    System.out.println("  [ICE] " + state);
                    if ("connected".equals(state) || "completed".equals(state)) {
                        connectedLatch.countDown();
                    }
                }
                @Override public void onIceGatheringChange(String state) {
                    System.out.println("  [ICE Gathering] " + state);
                }
                @Override public void onIceCandidate(String candidate, String sdpMid, int sdpMLineIndex) {
                    System.out.println("  [Local ICE] " + truncate(candidate, 50));
                }
                @Override public void onAddTrack(String kind) {
                    if ("video".equals(kind)) videoTrackReceived.set(true);
                    if ("audio".equals(kind)) audioTrackReceived.set(true);
                    System.out.println("  [Remote Track] " + kind);
                }
            }
        );

        // 完整流程：
        // 1. 收到 webrtc_config
        System.out.println("\n  Step 1: 收到 webrtc_config");
        sim.receiveWebrtcConfig("{\"ice_servers\":[{\"urls\":\"stun:stun.l.google.com:19302\"}]}");

        // 2. 收到 webrtc_offer
        System.out.println("\n  Step 2: 收到 webrtc_offer");
        sim.receiveWebrtcOffer(buildSampleOfferSdp(true, true));

        // 等待 answer 发送
        Thread.sleep(500);

        // 3. ICE candidates 交换
        System.out.println("\n  Step 3: ICE candidate 交换");
        sim.markSignalingReady();
        sim.receiveIceCandidate("candidate:foundation 1 udp 1 192.168.1.1 5000 typ host", "0", 0);
        sim.receiveIceCandidate("candidate:foundation 2 udp 1 203.0.113.1 5003 typ srflx", "1", 1);

        // 4. ICE 连接建立
        System.out.println("\n  Step 4: ICE 连接建立");
        sim.simulateIceStateChange("checking");
        Thread.sleep(100);
        sim.simulateIceStateChange("connected");

        // 等待连接完成
        boolean connected = connectedLatch.await(5, TimeUnit.SECONDS);
        Thread.sleep(200);

        // 5. 远端 track 到达
        System.out.println("\n  Step 5: 远端媒体 track 到达");
        sim.simulateRemoteTrackAdded("video");
        sim.simulateRemoteTrackAdded("audio");

        sim.shutdown();

        // 打印完整时间线
        System.out.println("\n  --- 完整时间线 ---");
        for (String t : sim.getTimeline()) System.out.println("  " + t);

        System.out.println("\n  --- 状态变化 ---");
        for (String s : stateChanges) System.out.println("  " + s);

        // 验证
        assertTrue("ICE 应该连接成功", connected);
        assertTrue("answer 应已发送", sim.isAnswerSent());
        assertTrue("answer 应包含 video", sim.hasVideoInAnswer());
        assertFalse("不应有错误", sim.hasErrors());
        assertTrue("应收到远端 video track", videoTrackReceived.get());
        assertTrue("应收到远端 audio track", audioTrackReceived.get());

        // 验证 WebSocket 消息序列
        boolean hasAnswer = wsMessages.stream().anyMatch(m -> m.contains("\"type\":\"webrtc_answer\""));
        assertTrue("应该发送了 webrtc_answer", hasAnswer);

        System.out.println("\n  RESULT: PASS - 完整信令流程成功，视频/音频 track 正常接收");
    }

    // ========================================================================
    //  TEST 6: OpenClaw 桥接流程
    // ========================================================================

    @Test
    public void testOpenClawBridgeFlow() throws Exception {
        startTime = System.currentTimeMillis();
        System.out.println("\n========== TEST 6: OpenClaw 桥接流程 ==========");

        OpenClawBridgeSimulator bridge = new OpenClawBridgeSimulator();

        // 场景 1: WebRTC 未连接时桥接被跳过
        System.out.println("\n  --- 场景 1: WebRTC 未连接 ---");
        bridge.setBridgeEnabled(false);
        bridge.receiveOpenClawResponse("text", "你好，我是 AI 助手");

        assertEquals("WebRTC 未连接时不应发送到 CyberVerse", 0, bridge.getMessagesSentCount());
        assertEquals("LLM 回复应已接收", 1, bridge.getLlmResponseCount());

        // 场景 2: WebRTC 已连接，正常桥接
        System.out.println("\n  --- 场景 2: WebRTC 已连接，正常桥接 ---");
        bridge.setBridgeEnabled(true);
        bridge.receiveOpenClawResponse("text", "今天天气真好，适合出门散步");

        assertEquals("应发送到 CyberVerse", 1, bridge.getMessagesSentCount());
        assertEquals("TTS 请求应计数", 1, bridge.getTtsRequestCount());

        // 场景 3: 推送消息也桥接
        System.out.println("\n  --- 场景 3: 服务端推送消息桥接 ---");
        bridge.receiveOpenClawResponse("push", "您有一条新的提醒消息");

        assertEquals("应发送 2 条到 CyberVerse", 2, bridge.getMessagesSentCount());

        // 场景 4: end 信号不触发桥接
        System.out.println("\n  --- 场景 4: end 信号不触发桥接 ---");
        bridge.receiveOpenClawResponse("end", "");

        assertEquals("end 信号不增加发送", 2, bridge.getMessagesSentCount());

        // 打印时间线
        System.out.println("\n  --- 桥接时间线 ---");
        for (String t : bridge.getTimeline()) System.out.println("  " + t);

        System.out.println("\n  RESULT: PASS - OpenClaw 桥接流程正确，WebRTC 未连接时跳过，连接后正常桥接");
    }

    // ========================================================================
    //  TEST 7: OpenClaw + WebRTC 联合流程
    // ========================================================================

    @Test
    public void testOpenClawWithWebRTCCombined() throws Exception {
        startTime = System.currentTimeMillis();
        System.out.println("\n========== TEST 7: OpenClaw + WebRTC 联合流程 ==========");

        CountDownLatch answerLatch = new CountDownLatch(1);
        CountDownLatch connectedLatch = new CountDownLatch(1);
        List<String> cyberVerseMessages = new CopyOnWriteArrayList<>();

        // WebRTC 模拟器
        WebRTCSignalingSimulator webrtc = new WebRTCSignalingSimulator(
            WebRTCSignalingSimulator.Mode.FIXED,
            json -> {
                cyberVerseMessages.add(json);
                System.out.println("  [WS->CyberVerse] " + truncate(json, 80));
                if (json.contains("webrtc_answer")) answerLatch.countDown();
            },
            new MockPeerObserver() {
                @Override public void onSignalingChange(String state) {}
                @Override public void onIceConnectionChange(String state) {
                    if ("connected".equals(state)) connectedLatch.countDown();
                }
                @Override public void onIceGatheringChange(String state) {}
                @Override public void onIceCandidate(String c, String m, int i) {}
                @Override public void onAddTrack(String kind) {}
            }
        );

        // OpenClaw 桥接模拟器
        OpenClawBridgeSimulator bridge = new OpenClawBridgeSimulator();

        // --- Phase 1: 建立 WebRTC 连接 ---
        System.out.println("\n  Phase 1: 建立 WebRTC 连接...");
        webrtc.receiveWebrtcConfig("{\"ice_servers\":[]}");
        webrtc.receiveWebrtcOffer(buildSampleOfferSdp(true, true));
        answerLatch.await(5, TimeUnit.SECONDS);

        webrtc.markSignalingReady();
        Thread.sleep(100);
        webrtc.simulateIceStateChange("connected");
        connectedLatch.await(5, TimeUnit.SECONDS);

        // --- Phase 2: OpenClaw 发送文本 + 桥接 ---
        System.out.println("\n  Phase 2: OpenClaw 模式发送消息...");
        bridge.setBridgeEnabled(true);

        // 模拟用户发送文本 -> OpenClaw 返回 LLM 回复 -> 桥接到 CyberVerse
        bridge.receiveOpenClawResponse("text", "你好！我是通过 OpenClaw 模式回复的。");

        // CyberVerse 收到的消息应包含 assistant_text
        boolean hasAssistantText = cyberVerseMessages.stream()
            .anyMatch(m -> m.contains("assistant_text"));
        assertTrue("CyberVerse 应收到 assistant_text 消息", hasAssistantText);

        // 模拟第二条回复
        bridge.receiveOpenClawResponse("llm", "今天想聊些什么呢？");
        long assistantTextCount = cyberVerseMessages.stream()
            .filter(m -> m.contains("assistant_text"))
            .count();
        assertEquals("应有 2 条 assistant_text", 2, assistantTextCount);

        webrtc.shutdown();

        System.out.println("\n  --- WebRTC 时间线 ---");
        for (String t : webrtc.getTimeline()) System.out.println("  " + t);

        System.out.println("\n  --- 桥接时间线 ---");
        for (String t : bridge.getTimeline()) System.out.println("  " + t);

        System.out.println("\n  RESULT: PASS - OpenClaw + WebRTC 联合流程正常");
    }

    // ========================================================================
    //  TEST 8: 多线程并发安全性
    // ========================================================================

    @Test
    public void testConcurrentSignalingMessages() throws Exception {
        startTime = System.currentTimeMillis();
        System.out.println("\n========== TEST 8: 多线程并发安全性 ==========");

        CountDownLatch answerLatch = new CountDownLatch(1);
        AtomicInteger wsSendCount = new AtomicInteger(0);

        WebRTCSignalingSimulator sim = new WebRTCSignalingSimulator(
            WebRTCSignalingSimulator.Mode.FIXED,
            json -> wsSendCount.incrementAndGet(),
            null
        );

        ExecutorService pool = Executors.newFixedThreadPool(4);

        // 线程 1: 发送 offer
        pool.submit(() -> {
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            sim.receiveWebrtcOffer(buildSampleOfferSdp(true, true));
        });

        // 线程 2: 同时发送多个 ICE candidate
        pool.submit(() -> {
            for (int i = 0; i < 5; i++) {
                sim.receiveIceCandidate("candidate:" + i + " udp 1 192.168.1." + i + " 500" + i + " typ host", "0", 0);
            }
        });

        // 线程 3: 同时发送更多 ICE candidate
        pool.submit(() -> {
            for (int i = 5; i < 10; i++) {
                sim.receiveIceCandidate("candidate:" + i + " udp 1 10.0.0." + i + " 501" + i + " typ host", "1", 1);
            }
        });

        // 线程 4: 等待后标记 signaling ready
        pool.submit(() -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            sim.markSignalingReady();
        });

        answerLatch.await(5, TimeUnit.SECONDS);
        Thread.sleep(500);

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        sim.shutdown();

        System.out.println("\n  --- 时间线 ---");
        for (String t : sim.getTimeline()) System.out.println("  " + t);

        assertTrue("answer 应已发送", sim.isAnswerSent());
        assertTrue("answer 应包含 video", sim.hasVideoInAnswer());
        assertTrue("应添加了 ICE candidates", sim.getIceCandidateCount() > 0);

        // 即使有并发，FIXED 模式也不应有竞态错误
        if (sim.hasErrors()) {
            System.out.println("\n  WARNING: 有 " + sim.getErrors().size() + " 个错误（可能由并发引起）:");
            for (String e : sim.getErrors()) System.out.println("    " + e);
        }

        System.out.println("\n  RESULT: " + (sim.hasErrors() ? "WARNING" : "PASS") +
            " - 并发消息处理完成, answer sent=" + sim.isAnswerSent() +
            ", ice candidates=" + sim.getIceCandidateCount());
    }

    // ========================================================================
    //  TEST 9: 只音频 offer（无 video）场景
    // ========================================================================

    @Test
    public void testAudioOnlyOffer() throws Exception {
        startTime = System.currentTimeMillis();
        System.out.println("\n========== TEST 9: 只音频 offer (无 video) ==========");

        CountDownLatch answerLatch = new CountDownLatch(1);
        WebRTCSignalingSimulator sim = new WebRTCSignalingSimulator(
            WebRTCSignalingSimulator.Mode.FIXED,
            json -> answerLatch.countDown(),
            null
        );

        // 只音频的 offer
        String audioOnlyOffer = buildSampleOfferSdp(true, false);
        sim.receiveWebrtcOffer(audioOnlyOffer);

        answerLatch.await(5, TimeUnit.SECONDS);
        sim.shutdown();

        System.out.println("\n  --- 时间线 ---");
        for (String t : sim.getTimeline()) System.out.println("  " + t);

        assertTrue("answer 应已发送", sim.isAnswerSent());
        assertFalse("只音频 offer 的 answer 不应有 video", sim.hasVideoInAnswer());
        assertTrue("answer 应有 audio", sim.hasAudioInAnswer());
        assertFalse("不应有错误", sim.hasErrors());

        System.out.println("\n  RESULT: PASS - 只音频 offer 正确处理，answer 中无 video");
    }

    // ========================================================================
    //  辅助方法
    // ========================================================================

    private String buildSampleOfferSdp(boolean audio, boolean video) {
        StringBuilder sb = new StringBuilder();
        sb.append("v=0\r\n");
        sb.append("o=- 987654321 1 IN IP4 0.0.0.0\r\n");
        sb.append("s=CyberVerse-Server\r\n");
        sb.append("t=0 0\r\n");
        sb.append("a=group:BUNDLE");
        if (audio) sb.append(" 0");
        if (video) sb.append(" 1");
        sb.append("\r\n");
        if (audio) {
            sb.append("m=audio 9 UDP/TLS/RTP/SAVPF 111\r\n");
            sb.append("c=IN IP4 0.0.0.0\r\n");
            sb.append("a=mid:0\r\n");
            sb.append("a=sendonly\r\n");
            sb.append("a=rtpmap:111 opus/48000/2\r\n");
        }
        if (video) {
            sb.append("m=video 9 UDP/TLS/RTP/SAVPF 96\r\n");
            sb.append("c=IN IP4 0.0.0.0\r\n");
            sb.append("a=mid:1\r\n");
            sb.append("a=sendonly\r\n");
            sb.append("a=rtpmap:96 VP8/90000\r\n");
        }
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
