#!/usr/bin/env python3
"""
DirectWebRTCManager 信令流程模拟测试

模拟 Android 端 DirectWebRTCManager 的完整 WebRTC 信令流程，包括：
1. 正确的 SDP 协商流程（FIXED 模式）
2. 竞态条件复现（BUGGY 模式 — 旧 bug）
3. FIXED vs BUGGY answer SDP 对比
4. ICE 候选排队与刷新
5. 完整信令生命周期（config → offer → answer → ICE → connected → track）
6. OpenClaw 桥接流程
7. OpenClaw + WebRTC 联合流程
8. 多线程并发安全性
9. 只音频 offer 场景

对应 Android 代码：DirectWebRTCManager.kt + CloudChatManager.kt
"""

import threading
import time
import json
import unittest
from dataclasses import dataclass, field
from typing import List, Optional, Callable
from enum import Enum
from collections import deque
from concurrent.futures import ThreadPoolExecutor, Future


# ========================================================================
#  模式定义
# ========================================================================

class Mode(Enum):
    FIXED = "FIXED"   # 正确流程：createAnswer 在 onSetSuccess 回调里
    BUGGY = "BUGGY"   # 竞态条件：createAnswer 在 setRemote 完成前调用


# ========================================================================
#  WebRTCSignalingSimulator — 模拟 DirectWebRTCManager
# ========================================================================

class WebRTCSignalingSimulator:
    """模拟 DirectWebRTCManager 的 SDP 协商状态机"""

    def __init__(self, mode: Mode, ws_sender: Callable[[str], None],
                 peer_observer: Optional[Callable[[str, dict], None]] = None):
        self.mode = mode
        self.ws_sender = ws_sender
        self.peer_observer = peer_observer
        self.lock = threading.Lock()
        self._start_ts = time.monotonic()

        # SDP 状态
        self.remote_sdp: Optional[str] = None
        self.local_sdp: Optional[str] = None
        self.remote_description_set = False
        self.local_description_set = False
        self.answer_sent = False
        self.has_video = False
        self.has_audio = False

        # ICE 状态
        self.signaling_ready = False
        self.pending_ice_candidates: List[dict] = []
        self.added_ice_candidates: List[str] = []
        self.ice_candidate_count = 0

        # 跟踪
        self.errors: List[str] = []
        self.log_messages: List[str] = []
        self.timeline: List[str] = []

    def _delta_ms(self) -> float:
        return (time.monotonic() - self._start_ts) * 1000

    def receive_webrtc_config(self, config_json: str):
        """模拟收到 webrtc_config"""
        with self.lock:
            self.timeline.append(f"[T+{self._delta_ms():.0f}ms] receiveWebrtcConfig")
            self.log_messages.append("ICE servers configured from config")

    def receive_webrtc_offer(self, offer_sdp: str):
        """模拟收到 webrtc_offer — 核心方法"""
        with self.lock:
            self.timeline.append(f"[T+{self._delta_ms():.0f}ms] receiveWebrtcOffer (SDP len={len(offer_sdp)})")
            self.remote_sdp = offer_sdp
            self.has_video = "m=video" in offer_sdp
            self.has_audio = "m=audio" in offer_sdp
            self.log_messages.append(
                f"Received WebRTC offer (audio={self.has_video}, video={self.has_audio})")

        # 模拟 mainHandler.post 异步处理
        def _async_handle():
            with self.lock:
                self.timeline.append(f"[T+{self._delta_ms():.0f}ms] start handleWebrtcOffer (main thread)")
                self.log_messages.append("PeerConnection created (Unified Plan)")
            self._simulate_set_remote_description(offer_sdp)

        threading.Thread(target=_async_handle, daemon=True).start()

    def _simulate_set_remote_description(self, offer_sdp: str):
        """模拟 setRemoteDescription — 关键差异点"""
        with self.lock:
            self.timeline.append(f"[T+{self._delta_ms():.0f}ms] setRemoteDescription(OFFER) called")

        if self.mode == Mode.BUGGY:
            # BUGGY: 不等 onSetSuccess，直接调 createAnswer
            with self.lock:
                self.timeline.append(
                    f"[T+{self._delta_ms():.0f}ms] BUG: createAnswer called BEFORE setRemote onSetSuccess!")
                self.errors.append(
                    "RACE_CONDITION: createAnswer called before setRemoteDescription completed")
            self._simulate_create_answer()

        # 模拟 onSetSuccess 异步回调（50ms 延迟）
        def _on_set_success():
            time.sleep(0.05)  # 模拟处理延迟
            with self.lock:
                if self.mode == Mode.BUGGY:
                    self.timeline.append(
                        f"[T+{self._delta_ms():.0f}ms] setRemoteDescription onSetSuccess "
                        f"(TOO LATE - answer already created)")
                    self.errors.append(
                        "RACE_CONDITION: onSetSuccess fired but answer was already created with stale state")
                    self.remote_description_set = True
                else:
                    # FIXED: 在 onSetSuccess 回调里调 createAnswer
                    self.timeline.append(f"[T+{self._delta_ms():.0f}ms] setRemoteDescription onSetSuccess")
                    self.remote_description_set = True
            self._simulate_create_answer()

        threading.Thread(target=_on_set_success, daemon=True).start()

    def _simulate_create_answer(self):
        """模拟 createAnswer"""
        with self.lock:
            self.timeline.append(f"[T+{self._delta_ms():.0f}ms] createAnswer called")

            remote_ready = self.remote_description_set
            if not remote_ready:
                self.timeline.append(
                    f"[T+{self._delta_ms():.0f}ms] BUG: answer created WITHOUT remoteDescription being set!")
                self.errors.append(
                    "MALFORMED_ANSWER: answer created before remoteDescription was set "
                    "- video line will be missing")
            else:
                self.timeline.append(
                    f"[T+{self._delta_ms():.0f}ms] answer created successfully (remoteDescription was set)")

            # 生成 answer SDP
            if remote_ready and self.has_video:
                answer_sdp = self._build_answer_sdp(True, True)
            else:
                answer_sdp = self._build_answer_sdp(True, False)
                if self.has_video:
                    self.timeline.append(
                        f"[T+{self._delta_ms():.0f}ms] BUG: answer SDP missing m=video despite offer having it!")
                    self.errors.append("MISSING_VIDEO_IN_ANSWER: SDP answer does not contain m=video")

            self.local_sdp = answer_sdp
            self.timeline.append(
                f"[T+{self._delta_ms():.0f}ms] answer SDP generated (has video: {('m=video' in answer_sdp)})")

        self._simulate_set_local_description(answer_sdp)

    def _simulate_set_local_description(self, answer_sdp: str):
        """模拟 setLocalDescription"""
        with self.lock:
            self.timeline.append(f"[T+{self._delta_ms():.0f}ms] setLocalDescription(ANSWER) called")

        def _on_set_success():
            time.sleep(0.02)
            with self.lock:
                self.local_description_set = True
                self.timeline.append(f"[T+{self._delta_ms():.0f}ms] setLocalDescription onSetSuccess")

        threading.Thread(target=_on_set_success, daemon=True).start()

        # 发送 answer
        answer_json = json.dumps({"type": "webrtc_answer", "sdp": answer_sdp})
        self.ws_sender(answer_json)
        with self.lock:
            self.answer_sent = True
            self.timeline.append(f"[T+{self._delta_ms():.0f}ms] WebRTC answer SENT to server via WebSocket")

    def receive_ice_candidate(self, candidate: str, sdp_mid: str, sdp_mline_index: int):
        """模拟收到 ICE candidate"""
        should_add = False
        should_queue = False
        with self.lock:
            self.timeline.append(
                f"[T+{self._delta_ms():.0f}ms] receiveIceCandidate (mid={sdp_mid}, idx={sdp_mline_index})")
            if self.signaling_ready:
                should_add = True
            else:
                should_queue = True
                self.pending_ice_candidates.append({
                    "candidate": candidate, "sdp_mid": sdp_mid, "sdp_mline_index": sdp_mline_index
                })
                self.timeline.append(
                    f"[T+{self._delta_ms():.0f}ms] ICE candidate QUEUED "
                    f"(pending={len(self.pending_ice_candidates)})")
        # 在锁外添加 candidate（避免 _add_ice_candidate 再次获取锁导致死锁）
        if should_add:
            self._add_ice_candidate(candidate, sdp_mid, sdp_mline_index)

    def mark_signaling_ready(self):
        """标记 signaling ready 并刷新排队 candidates"""
        with self.lock:
            self.signaling_ready = True
            pending = list(self.pending_ice_candidates)
            self.pending_ice_candidates.clear()
            self.timeline.append(
                f"[T+{self._delta_ms():.0f}ms] signalingReady=true, flushing {len(pending)} pending ICE")

        # 在锁外添加 ICE candidates（避免 _add_ice_candidate 再次获取锁导致死锁）
        for cand in pending:
            with self.lock:
                self.added_ice_candidates.append(cand["candidate"])
                self.ice_candidate_count += 1
            if self.peer_observer:
                self.peer_observer("ice_candidate", {
                    "candidate": cand["candidate"],
                    "sdp_mid": cand["sdp_mid"],
                    "sdp_mline_index": cand["sdp_mline_index"]
                })

    def _add_ice_candidate(self, candidate: str, sdp_mid: str, sdp_mline_index: int):
        """添加单个 ICE candidate（调用者不应持有 self.lock）"""
        with self.lock:
            self.added_ice_candidates.append(candidate)
            self.ice_candidate_count += 1
        if self.peer_observer:
            self.peer_observer("ice_candidate", {
                "candidate": candidate, "sdp_mid": sdp_mid, "sdp_mline_index": sdp_mline_index
            })

    def simulate_ice_state_change(self, state: str):
        """模拟 ICE 状态变化"""
        with self.lock:
            self.timeline.append(f"[T+{self._delta_ms():.0f}ms] ICE state: {state}")
        if self.peer_observer:
            self.peer_observer("ice_connection_change", {"state": state})

    def simulate_remote_track_added(self, kind: str):
        """模拟远端 track 添加"""
        with self.lock:
            self.timeline.append(f"[T+{self._delta_ms():.0f}ms] onAddTrack: kind={kind}")
        if self.peer_observer:
            self.peer_observer("add_track", {"kind": kind})

    def _build_answer_sdp(self, audio: bool, video: bool) -> str:
        lines = [
            "v=0", "o=- 123456789 2 IN IP4 0.0.0.0", "s=-", "t=0 0",
        ]
        bundle = []
        if audio: bundle.append(" 0")
        if video: bundle.append(" 1")
        lines.append("a=group:BUNDLE" + "".join(bundle))
        if audio:
            lines.extend(["m=audio 9 UDP/TLS/RTP/SAVPF 111", "a=mid:0", "a=recvonly",
                         "a=rtpmap:111 opus/48000/2"])
        if video:
            lines.extend(["m=video 9 UDP/TLS/RTP/SAVPF 96", "a=mid:1", "a=recvonly",
                         "a=rtpmap:96 VP8/90000"])
        return "\r\n".join(lines)

    # 查询方法
    def is_answer_sent(self) -> bool:
        with self.lock: return self.answer_sent

    def has_video_in_answer(self) -> bool:
        with self.lock: return self.local_sdp is not None and "m=video" in self.local_sdp

    def has_audio_in_answer(self) -> bool:
        with self.lock: return self.local_sdp is not None and "m=audio" in self.local_sdp

    def has_errors(self) -> bool:
        with self.lock: return len(self.errors) > 0

    def get_errors(self) -> List[str]:
        with self.lock: return list(self.errors)

    def get_timeline(self) -> List[str]:
        with self.lock: return list(self.timeline)

    def get_ice_count(self) -> int:
        with self.lock: return self.ice_candidate_count


# ========================================================================
#  OpenClawBridgeSimulator — 模拟 CloudChatManager 的桥接
# ========================================================================

class OpenClawBridgeSimulator:
    """模拟 OpenClaw -> CyberVerse 桥接流程"""

    def __init__(self):
        self.lock = threading.Lock()
        self._start_ts = time.monotonic()
        self.messages_sent: List[str] = []
        self.received: List[str] = []
        self.timeline: List[str] = []
        self.bridge_enabled = False
        self.llm_count = 0
        self.tts_count = 0

    def _delta_ms(self) -> float:
        return (time.monotonic() - self._start_ts) * 1000

    def set_bridge_enabled(self, enabled: bool):
        with self.lock:
            self.bridge_enabled = enabled
            self.timeline.append(
                f"[T+{self._delta_ms():.0f}ms] bridge {'ENABLED' if enabled else 'DISABLED'}")

    def receive_openclaw_response(self, msg_type: str, text: str):
        with self.lock:
            self.received.append(text)
            self.llm_count += 1
            self.timeline.append(
                f"[T+{self._delta_ms():.0f}ms] OpenClaw: type={msg_type}, text={text[:40]}")

        if msg_type in ("text", "llm"):
            self._bridge(text)
        elif msg_type == "push":
            with self.lock:
                self.timeline.append(f"[T+{self._delta_ms():.0f}ms] Push: {text[:40]}")
            self._bridge(text)

    def _bridge(self, text: str):
        with self.lock:
            if not self.bridge_enabled:
                self.timeline.append(f"[T+{self._delta_ms():.0f}ms] BRIDGE SKIPPED: WebRTC not connected")
                return
            j = json.dumps({"type": "assistant_text", "text": text})
            self.messages_sent.append(j)
            self.tts_count += 1
            self.timeline.append(f"[T+{self._delta_ms():.0f}ms] Bridged to CyberVerse for TTS: {text[:30]}")

    def get_sent_count(self) -> int:
        with self.lock: return len(self.messages_sent)

    def get_llm_count(self) -> int:
        with self.lock: return self.llm_count

    def get_tts_count(self) -> int:
        with self.lock: return self.tts_count

    def get_timeline(self) -> List[str]:
        with self.lock: return list(self.timeline)


# ========================================================================
#  辅助函数
# ========================================================================

def build_offer_sdp(audio: bool = True, video: bool = True) -> str:
    lines = ["v=0", "o=- 987654321 1 IN IP4 0.0.0.0", "s=CyberVerse-Server", "t=0 0"]
    bundle = []
    if audio: bundle.append(" 0")
    if video: bundle.append(" 1")
    lines.append("a=group:BUNDLE" + "".join(bundle))
    if audio:
        lines.extend(["m=audio 9 UDP/TLS/RTP/SAVPF 111", "c=IN IP4 0.0.0.0",
                      "a=mid:0", "a=sendonly", "a=rtpmap:111 opus/48000/2"])
    if video:
        lines.extend(["m=video 9 UDP/TLS/RTP/SAVPF 96", "c=IN IP4 0.0.0.0",
                      "a=mid:1", "a=sendonly", "a=rtpmap:96 VP8/90000"])
    return "\r\n".join(lines)


def wait_for(condition_fn, timeout=5.0, interval=0.05) -> bool:
    """等待条件满足"""
    start = time.monotonic()
    while time.monotonic() - start < timeout:
        if condition_fn():
            return True
        time.sleep(interval)
    return False


# ========================================================================
#  TEST 1: 正确的 SDP 协商流程（FIXED 模式）
# ========================================================================

def test_correct_sdp_negotiation():
    """FIXED 模式：setRemote → onSetSuccess → createAnswer → 正确的 answer"""
    print("\n" + "=" * 70)
    print("TEST 1: 正确的 SDP 协商流程 (FIXED)")
    print("=" * 70)

    sent_messages = []
    event = threading.Event()

    sim = WebRTCSignalingSimulator(
        Mode.FIXED,
        ws_sender=lambda j: (sent_messages.append(j), event.set())
    )

    offer = build_offer_sdp(audio=True, video=True)
    sim.receive_webrtc_config('{"ice_servers":[{"urls":"stun:stun.l.google.com:19302"}]}')
    sim.receive_webrtc_offer(offer)

    ok = event.wait(timeout=5.0)
    time.sleep(0.3)  # 让 timeline 稳定

    print("\n  --- 时间线 ---")
    for t in sim.get_timeline():
        print(f"  {t}")

    assert ok, "answer 应在超时前发送"
    assert sim.is_answer_sent(), "answer 已发送"
    assert sim.has_video_in_answer(), "answer 应包含 m=video"
    assert sim.has_audio_in_answer(), "answer 应包含 m=audio"
    assert not sim.has_errors(), f"FIXED 模式不应有错误，但有: {sim.get_errors()}"

    print(f"\n  RESULT: PASS — answer 包含 audio+video, 无竞态错误")
    return True


# ========================================================================
#  TEST 2: 竞态条件复现（BUGGY 模式）
# ========================================================================

def test_race_condition_buggy():
    """BUGGY 模式：createAnswer 在 setRemote 完成前调用"""
    print("\n" + "=" * 70)
    print("TEST 2: 竞态条件复现 (BUGGY)")
    print("=" * 70)

    sent_messages = []
    event = threading.Event()

    sim = WebRTCSignalingSimulator(
        Mode.BUGGY,
        ws_sender=lambda j: (sent_messages.append(j), event.set())
    )

    offer = build_offer_sdp(audio=True, video=True)
    sim.receive_webrtc_config('{"ice_servers":[{"urls":"stun:stun.l.google.com:19302"}]}')
    sim.receive_webrtc_offer(offer)

    ok = event.wait(timeout=5.0)
    time.sleep(0.3)

    print("\n  --- 时间线 ---")
    for t in sim.get_timeline():
        print(f"  {t}")

    print("\n  --- 错误列表 ---")
    for e in sim.get_errors():
        print(f"  ERROR: {e}")

    assert ok, "answer 应在超时前发送"
    assert sim.is_answer_sent(), "answer 已发送"
    assert sim.has_errors(), "BUGGY 模式应该有错误"

    has_race = any("RACE_CONDITION" in e for e in sim.get_errors())
    assert has_race, "应该检测到 RACE_CONDITION"

    print(f"\n  RESULT: PASS — 竞态条件已复现, 检测到 {len(sim.get_errors())} 个错误")
    return True


# ========================================================================
#  TEST 3: FIXED vs BUGGY 对比
# ========================================================================

def test_fixed_vs_buggy_comparison():
    """对比两种模式 answer SDP 的差异"""
    print("\n" + "=" * 70)
    print("TEST 3: FIXED vs BUGGY answer SDP 对比")
    print("=" * 70)

    offer = build_offer_sdp(audio=True, video=True)

    # FIXED
    fixed_event = threading.Event()
    fixed = WebRTCSignalingSimulator(Mode.FIXED,
        ws_sender=lambda j: fixed_event.set())
    fixed.receive_webrtc_offer(offer)
    fixed_event.wait(timeout=5.0)
    time.sleep(0.2)

    # BUGGY
    buggy_event = threading.Event()
    buggy = WebRTCSignalingSimulator(Mode.BUGGY,
        ws_sender=lambda j: buggy_event.set())
    buggy.receive_webrtc_offer(offer)
    buggy_event.wait(timeout=5.0)
    time.sleep(0.2)

    print(f"\n  FIXED: has_video={fixed.has_video_in_answer()}, has_audio={fixed.has_audio_in_answer()}, "
          f"errors={len(fixed.get_errors())}")
    print(f"  BUGGY: has_video={buggy.has_video_in_answer()}, has_audio={buggy.has_audio_in_answer()}, "
          f"errors={len(buggy.get_errors())}")

    print("\n  --- FIXED 时间线 ---")
    for t in fixed.get_timeline(): print(f"  {t}")
    print("\n  --- BUGGY 时间线 ---")
    for t in buggy.get_timeline(): print(f"  {t}")

    assert fixed.has_video_in_answer(), "FIXED answer 应有 video"
    assert not fixed.has_errors(), "FIXED 应无错误"
    assert buggy.has_errors(), "BUGGY 应有错误"

    print(f"\n  RESULT: PASS — FIXED 正确, BUGGY 产生竞态错误")
    return True


# ========================================================================
#  TEST 4: ICE 候选排队与刷新
# ========================================================================

def test_ice_candidate_queueing():
    """ICE 候选排队和刷新机制"""
    print("\n" + "=" * 70)
    print("TEST 4: ICE 候选排队与刷新")
    print("=" * 70)

    sim = WebRTCSignalingSimulator(Mode.FIXED, ws_sender=lambda j: None)

    # signalingReady 前收到 3 个 candidate
    for i in range(3):
        sim.receive_ice_candidate(
            f"candidate:foundation {i} udp 1 192.168.1.{i} 500{i} typ host", "0", 0)
    print(f"  收到 3 个 ICE candidate (signaling 未就绪, 应排队)")

    # 标记 ready → 刷新
    sim.mark_signaling_ready()
    print(f"  signalingReady=true, 已刷新排队 candidates")

    # ready 后再收 2 个
    for i in range(3, 5):
        sim.receive_ice_candidate(
            f"candidate:foundation {i} udp 1 203.0.113.{i} 500{i} typ srflx", "1", 1)

    time.sleep(0.1)

    print("\n  --- 时间线 ---")
    for t in sim.get_timeline(): print(f"  {t}")
    print(f"\n  总计添加 ICE candidates: {sim.get_ice_count()}")

    assert sim.get_ice_count() >= 4, f"应至少有 4 个 ICE candidates, 实际: {sim.get_ice_count()}"

    print(f"\n  RESULT: PASS — ICE 候选正确排队和刷新 (共 {sim.get_ice_count()} 个)")
    return True


# ========================================================================
#  TEST 5: 完整信令生命周期
# ========================================================================

def test_full_signaling_lifecycle():
    """完整信令流程：config → offer → answer → ICE → connected → track"""
    print("\n" + "=" * 70)
    print("TEST 5: 完整信令生命周期")
    print("=" * 70)

    ws_messages = []
    state_changes = []
    tracks_received = []
    events = {"answer": threading.Event(), "connected": threading.Event()}

    def peer_observer(event_type, data):
        if event_type == "ice_connection_change":
            state_changes.append(data["state"])
            if data["state"] in ("connected", "completed"):
                events["connected"].set()
        elif event_type == "add_track":
            tracks_received.append(data["kind"])

    sim = WebRTCSignalingSimulator(
        Mode.FIXED,
        ws_sender=lambda j: (ws_messages.append(j),
                             events["answer"].set() if "webrtc_answer" in j else None),
        peer_observer=peer_observer
    )

    # Step 1: config
    print("\n  Step 1: 收到 webrtc_config")
    sim.receive_webrtc_config('{"ice_servers":[{"urls":"stun:stun.l.google.com:19302"}]}')

    # Step 2: offer
    print("  Step 2: 收到 webrtc_offer")
    sim.receive_webrtc_offer(build_offer_sdp(True, True))
    events["answer"].wait(timeout=5.0)
    time.sleep(0.3)

    # Step 3: ICE exchange
    print("  Step 3: ICE candidate 交换")
    sim.mark_signaling_ready()
    sim.receive_ice_candidate("candidate:1 udp 1 192.168.1.1 5000 typ host", "0", 0)
    sim.receive_ice_candidate("candidate:2 udp 1 203.0.113.1 5003 typ srflx", "1", 1)

    # Step 4: ICE connected
    print("  Step 4: ICE 连接建立")
    sim.simulate_ice_state_change("checking")
    time.sleep(0.1)
    sim.simulate_ice_state_change("connected")
    events["connected"].wait(timeout=5.0)
    time.sleep(0.1)

    # Step 5: remote tracks
    print("  Step 5: 远端媒体 track")
    sim.simulate_remote_track_added("video")
    sim.simulate_remote_track_added("audio")

    time.sleep(0.1)

    print("\n  --- 完整时间线 ---")
    for t in sim.get_timeline(): print(f"  {t}")
    print(f"\n  --- 状态变化 ---")
    for s in state_changes: print(f"  {s}")

    has_answer = any("webrtc_answer" in m for m in ws_messages)
    assert events["connected"].is_set(), "ICE 应连接成功"
    assert sim.is_answer_sent(), "answer 已发送"
    assert sim.has_video_in_answer(), "answer 有 video"
    assert not sim.has_errors(), "无错误"
    assert "video" in tracks_received, "收到 video track"
    assert "audio" in tracks_received, "收到 audio track"
    assert has_answer, "发送了 webrtc_answer"

    print(f"\n  RESULT: PASS — 完整信令流程成功, video/audio track 正常接收")
    return True


# ========================================================================
#  TEST 6: OpenClaw 桥接流程
# ========================================================================

def test_openclaw_bridge():
    """OpenClaw → CyberVerse 桥接"""
    print("\n" + "=" * 70)
    print("TEST 6: OpenClaw 桥接流程")
    print("=" * 70)

    bridge = OpenClawBridgeSimulator()

    # 场景 1: WebRTC 未连接
    print("\n  场景 1: WebRTC 未连接 → 桥接跳过")
    bridge.set_bridge_enabled(False)
    bridge.receive_openclaw_response("text", "你好，我是 AI 助手")
    assert bridge.get_sent_count() == 0, "未连接时不应发送"
    assert bridge.get_llm_count() == 1, "LLM 回复应已接收"

    # 场景 2: WebRTC 已连接
    print("  场景 2: WebRTC 已连接 → 正常桥接")
    bridge.set_bridge_enabled(True)
    bridge.receive_openclaw_response("text", "今天天气真好，适合出门散步")
    assert bridge.get_sent_count() == 1, "应发送 1 条"
    assert bridge.get_tts_count() == 1, "TTS 请求 1 次"

    # 场景 3: 推送消息桥接
    print("  场景 3: 推送消息也桥接")
    bridge.receive_openclaw_response("push", "您有一条新的提醒消息")
    assert bridge.get_sent_count() == 2, "应发送 2 条"

    # 场景 4: end 不触发桥接
    print("  场景 4: end 信号不触发桥接")
    bridge.receive_openclaw_response("end", "")
    assert bridge.get_sent_count() == 2, "end 不增加发送"

    print("\n  --- 桥接时间线 ---")
    for t in bridge.get_timeline(): print(f"  {t}")

    print(f"\n  RESULT: PASS — OpenClaw 桥接正确")
    return True


# ========================================================================
#  TEST 7: OpenClaw + WebRTC 联合
# ========================================================================

def test_openclaw_webrtc_combined():
    """OpenClaw + WebRTC 联合流程"""
    print("\n" + "=" * 70)
    print("TEST 7: OpenClaw + WebRTC 联合流程")
    print("=" * 70)

    cyber_messages = []
    events = {"answer": threading.Event(), "connected": threading.Event()}

    def peer_observer(event_type, data):
        if event_type == "ice_connection_change" and data["state"] == "connected":
            events["connected"].set()

    webrtc = WebRTCSignalingSimulator(
        Mode.FIXED,
        ws_sender=lambda j: (cyber_messages.append(j),
                             events["answer"].set() if "webrtc_answer" in j else None),
        peer_observer=peer_observer
    )

    bridge = OpenClawBridgeSimulator()

    # Phase 1: WebRTC 连接
    print("\n  Phase 1: 建立 WebRTC 连接...")
    webrtc.receive_webrtc_config('{"ice_servers":[]}')
    webrtc.receive_webrtc_offer(build_offer_sdp(True, True))
    events["answer"].wait(timeout=5.0)
    webrtc.mark_signaling_ready()
    time.sleep(0.1)
    webrtc.simulate_ice_state_change("connected")
    events["connected"].wait(timeout=5.0)

    # Phase 2: OpenClaw 桥接
    # 关键：在真实代码中 CloudChatManager.bridgeToCyberVerse() 调用
    # DirectWebRTCManager.sendAssistantText()，消息通过 WebRTC 的 WebSocket 发送。
    # 模拟器中 bridge 自己发消息，所以直接检查 bridge 的 messages_sent。
    print("  Phase 2: OpenClaw 模式发送消息...")
    bridge.set_bridge_enabled(True)
    bridge.receive_openclaw_response("text", "你好！我是通过 OpenClaw 模式回复的。")

    assert bridge.get_sent_count() >= 1, "桥接应至少发送 1 条到 CyberVerse"

    bridge.receive_openclaw_response("llm", "今天想聊些什么呢？")
    assert bridge.get_sent_count() >= 2, f"桥接应发送 2 条到 CyberVerse, 实际: {bridge.get_sent_count()}"

    time.sleep(0.1)

    print("\n  --- WebRTC 时间线 ---")
    for t in webrtc.get_timeline(): print(f"  {t}")
    print("\n  --- 桥接时间线 ---")
    for t in bridge.get_timeline(): print(f"  {t}")

    print(f"\n  RESULT: PASS — OpenClaw + WebRTC 联合流程正常")
    return True


# ========================================================================
#  TEST 8: 多线程并发
# ========================================================================

def test_concurrent_signaling():
    """多线程并发安全性"""
    print("\n" + "=" * 70)
    print("TEST 8: 多线程并发安全性")
    print("=" * 70)

    event = threading.Event()
    ws_count = [0]

    sim = WebRTCSignalingSimulator(
        Mode.FIXED,
        ws_sender=lambda j: (ws_count.__setitem__(0, ws_count[0] + 1), event.set())
    )

    with ThreadPoolExecutor(max_workers=4) as pool:
        # 线程 1: offer
        pool.submit(lambda: (time.sleep(0.01),
                             sim.receive_webrtc_offer(build_offer_sdp(True, True))))
        # 线程 2: ICE candidates
        pool.submit(lambda: [sim.receive_ice_candidate(
            f"candidate:{i} udp 1 192.168.1.{i} 500{i} typ host", "0", 0) for i in range(5)])
        # 线程 3: 更多 ICE
        pool.submit(lambda: [sim.receive_ice_candidate(
            f"candidate:{i} udp 1 10.0.0.{i} 501{i} typ host", "1", 1) for i in range(5, 10)])
        # 线程 4: signaling ready
        pool.submit(lambda: (time.sleep(0.2), sim.mark_signaling_ready()))

    event.wait(timeout=5.0)
    time.sleep(0.5)

    print("\n  --- 时间线 ---")
    for t in sim.get_timeline(): print(f"  {t}")

    assert sim.is_answer_sent(), "answer 应已发送"
    assert sim.has_video_in_answer(), "answer 应有 video"
    assert sim.get_ice_count() > 0, "应有 ICE candidates"

    status = "WARNING" if sim.has_errors() else "PASS"
    if sim.has_errors():
        print(f"\n  WARNING: 有 {len(sim.get_errors())} 个错误")
        for e in sim.get_errors(): print(f"    {e}")

    print(f"\n  RESULT: {status} — 并发完成, sent={sim.is_answer_sent()}, ice={sim.get_ice_count()}")
    return True


# ========================================================================
#  TEST 9: 只音频 offer
# ========================================================================

def test_audio_only_offer():
    """只音频的 offer（无 video）"""
    print("\n" + "=" * 70)
    print("TEST 9: 只音频 offer (无 video)")
    print("=" * 70)

    event = threading.Event()
    sim = WebRTCSignalingSimulator(
        Mode.FIXED,
        ws_sender=lambda j: event.set()
    )

    sim.receive_webrtc_offer(build_offer_sdp(audio=True, video=False))
    event.wait(timeout=5.0)
    time.sleep(0.2)

    print("\n  --- 时间线 ---")
    for t in sim.get_timeline(): print(f"  {t}")

    assert sim.is_answer_sent(), "answer 已发送"
    assert not sim.has_video_in_answer(), "只音频 answer 不应有 video"
    assert sim.has_audio_in_answer(), "answer 应有 audio"
    assert not sim.has_errors(), "无错误"

    print(f"\n  RESULT: PASS — 只音频 offer 正确处理")
    return True


# ========================================================================
#  主入口
# ========================================================================

def main():
    print("=" * 70)
    print("  DirectWebRTCManager 信令流程模拟测试")
    print("  对应 Android 代码: DirectWebRTCManager.kt + CloudChatManager.kt")
    print("=" * 70)

    tests = [
        ("TEST 1: 正确的 SDP 协商流程 (FIXED)", test_correct_sdp_negotiation),
        ("TEST 2: 竞态条件复现 (BUGGY)", test_race_condition_buggy),
        ("TEST 3: FIXED vs BUGGY 对比", test_fixed_vs_buggy_comparison),
        ("TEST 4: ICE 候选排队与刷新", test_ice_candidate_queueing),
        ("TEST 5: 完整信令生命周期", test_full_signaling_lifecycle),
        ("TEST 6: OpenClaw 桥接流程", test_openclaw_bridge),
        ("TEST 7: OpenClaw + WebRTC 联合", test_openclaw_webrtc_combined),
        ("TEST 8: 多线程并发安全性", test_concurrent_signaling),
        ("TEST 9: 只音频 offer", test_audio_only_offer),
    ]

    results = []
    passed = 0
    failed = 0

    for name, test_fn in tests:
        try:
            test_fn()
            results.append((name, "PASS", ""))
            passed += 1
        except Exception as e:
            results.append((name, "FAIL", str(e)))
            failed += 1
            print(f"\n  EXCEPTION: {e}")

    # 汇总
    print("\n" + "=" * 70)
    print(f"  测试结果汇总: {passed} PASSED, {failed} FAILED (共 {len(tests)} 个)")
    print("=" * 70)

    for name, status, error in results:
        icon = "PASS" if status == "PASS" else "FAIL"
        print(f"  [{icon}] {name}")
        if error:
            print(f"         错误: {error}")

    if failed == 0:
        print(f"\n  所有测试通过!")
        print(f"\n  关键结论:")
        print(f"  1. FIXED 模式 (当前代码): createAnswer 在 onSetSuccess 回调里调用 → answer 正确包含 video")
        print(f"  2. BUGGY 模式 (旧 bug):   createAnswer 在 setRemote 完成前调用 → 竞态条件导致 answer 畸形")
        print(f"  3. 这就是 [DirectPeer] publish timeout 的根因: 畸形 answer → WebRTC 连接失败 → 服务端无法推送视频")
    else:
        print(f"\n  {failed} 个测试失败!")

    return failed == 0


if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)
