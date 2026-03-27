# TTS Optimization Log

## Architecture

5-model ONNX pipeline running on Android (PocketTTS):

```
text_conditioner (FP32, 16MB) ─┐
mimi_encoder (FP32, 70MB) ─────┤── cold path (run once)
                                │
flow_lm_main (INT8, 73MB) ─────┤
flow_lm_flow (INT8, 9.5MB) ────┤── hot path (run every frame, 3 calls/frame)
mimi_decoder (INT8, 22MB) ─────┘
```

Output: 24kHz mono, 1920 samples/frame (80ms per frame)

## Current Performance (2026-03-27)

Device benchmark with per-frame breakdown:

| Model | Avg/frame | Share |
|-------|-----------|-------|
| flow_lm_main | 13.0ms | 58% |
| flow_lm_flow | 0.0ms | ~0% |
| mimi_decoder | 9.5ms | 42% |
| **Total** | **22.5ms** | |

Compute RTFx: **3.5x realtime** (80ms audio / 22.5ms compute)
Streaming wall-clock: ~1.0x (blocked on AudioTrack playback)

## Optimizations Applied

### Already in place (before this session)
- INT8 quantization on 3 hot-path models
- Memory-mapped model loading (file-path sessions)
- Pre-allocated tensor reuse (flowSeqBuffer, flowXBuffer, scalars)
- Zero-copy state management (keep OrtSession.Result alive for KV-cache)
- XNNPACK execution provider with 4 threads
- Voice embedding caching (mimi_encoder runs once per voice)
- Pre-encoded voice (skip mimi_encoder on generate)
- Persistent RNG (avoid re-seeding per frame)
- Reusable empty-text tensor
- Cold/hot session options split (different thread configs)
- `allow_spinning = 1` for hot-path busy-wait

### Applied this session
- EOS threshold tightened: -4.0 -> -3.0 (fewer trailing frames)
- Frames after EOS reduced: 3 -> 1 (configurable)
- Per-frame timing instrumentation (nanoTime per model call)
- XNNPACK toggle for A/B benchmarking on INT8 models
- Synced tts-demo with all library optimizations (was missing zero-copy, XNNPACK, caching)
- Sentence splitting in PocketTtsProvider — splits multi-sentence text and generates/streams each sentence independently through the same AudioTrack. First sentence audio begins playing while subsequent sentences are still generating. Shorter KV-cache per sentence may also improve per-frame speed.

## Investigated but not viable

### NNAPI / NPU offload
- NNAPI EP missing critical transformer ops: LayerNormalization, Gelu, 1D Conv, ConvTranspose, Attention
- Would cause fragmented execution (some ops NPU, some CPU) with data transfer overhead
- INT8 support is UINT8 activations only (u8s8), requires conversion
- Per-channel quantization on MatMul unsupported
- **Verdict: not practical for this model architecture**

### QNN EP (Qualcomm Snapdragon only)
- Better operator coverage (LayerNorm, Gelu, ConvTranspose supported)
- Direct Hexagon NPU access, potentially 2-5x speedup
- But: fixed input shapes only, custom quantization toolchain, Snapdragon-only
- **Verdict: high effort, device-specific, deferred**

## Remaining optimization opportunities

### Not yet tried
2. **Convert models to .ort format** - pre-baked graph optimizations, 20-50% faster load time (runtime unchanged)
3. **Quantize cold-path models** - text_conditioner + mimi_encoder to INT8. One-shot cost, low priority.
4. **Evaluate non-autoregressive models** (Kokoro-82M, Matcha-TTS) - eliminates the per-frame AR loop entirely. Potentially 3-5x faster but major architecture change.
5. **Thread affinity / big.LITTLE core pinning** - ensure hot-path runs on big cores
6. **Reduce LATENT_DIM or model size** - requires retraining, not feasible short-term

### Ruled out
- **Fuse flow_lm_main + flow_lm_flow** - flow_lm_flow is 0.0ms per frame, no gain

### Where the time goes (targets)
- **flow_lm_main (13ms, 58%)** - autoregressive transformer, KV-cache lookup. Largest single cost.
- **mimi_decoder (9.5ms, 42%)** - latent-to-audio conversion. Second target.
- flow_lm_flow is negligible at 0ms, not worth optimizing.
