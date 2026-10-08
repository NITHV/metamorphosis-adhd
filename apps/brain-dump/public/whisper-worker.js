// On-device speech-to-text (Whisper tiny, English) for voice dumps.
// Runs in a Web Worker so the page stays responsive. Audio never leaves the device:
// only the model files (~40MB, cached by the browser after the first time) are downloaded.

const LIB = "https://cdn.jsdelivr.net/npm/@huggingface/transformers@4.3.1/dist/transformers.min.js";
const MODEL = "onnx-community/whisper-tiny.en";

const pipelines = {}; // device -> Promise<pipeline>

// navigator.gpu can exist without a fully usable adapter (old drivers, headless, some phones).
async function hasUsableGpu() {
  try {
    const adapter = await navigator.gpu?.requestAdapter();
    return Boolean(adapter && adapter.info);
  } catch {
    return false;
  }
}

function progress_callback(p) {
  if (p.status === "progress" && p.total) {
    self.postMessage({ type: "progress", file: p.file, loaded: p.loaded, total: p.total });
  }
}

function getTranscriber(device) {
  if (!pipelines[device]) {
    pipelines[device] = (async () => {
      const { pipeline } = await import(LIB);
      return pipeline("automatic-speech-recognition", MODEL, {
        device,
        dtype: device === "webgpu" ? { encoder_model: "fp32", decoder_model_merged: "q8" } : "q8",
        progress_callback,
      });
    })();
    pipelines[device].catch(() => delete pipelines[device]); // allow a retry after a failed download
  }
  return pipelines[device];
}

async function run(device, audio, id) {
  const transcriber = await getTranscriber(device);
  self.postMessage({ type: "status", id, status: "transcribing" });
  const output = await transcriber(audio, { chunk_length_s: 30, stride_length_s: 5 });
  return (Array.isArray(output) ? output.map((o) => o.text).join(" ") : output.text).trim();
}

self.onmessage = async (event) => {
  const { id, audio } = event.data; // audio: Float32Array, 16 kHz mono
  try {
    let text;
    if (await hasUsableGpu()) {
      try {
        text = await run("webgpu", audio, id);
      } catch {
        // WebGPU looked available but failed; plain WebAssembly works everywhere.
        text = await run("wasm", audio, id);
      }
    } else {
      text = await run("wasm", audio, id);
    }
    self.postMessage({ type: "result", id, text });
  } catch (err) {
    self.postMessage({ type: "error", id, message: err instanceof Error ? err.message : String(err) });
  }
};
