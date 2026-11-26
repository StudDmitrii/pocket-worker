// Minimal polyfills for bare V8 isolates (Android androidx.javascriptengine JavaScriptSandbox).
// Intentionally does NOT define process.versions.node so web-tree-sitter uses wasmBinary instead of fs.

if (typeof globalThis.self === "undefined") {
  globalThis.self = globalThis;
}

if (typeof globalThis.process === "undefined") {
  globalThis.process = { arch: "x64", env: {} };
}

if (typeof globalThis.TextEncoder === "undefined") {
  globalThis.TextEncoder = class TextEncoder {
    encode(input = "") {
      const str = String(input);
      const out = [];
      for (let i = 0; i < str.length; i++) {
        let cp = str.charCodeAt(i);
        if (cp >= 0xd800 && cp <= 0xdbff && i + 1 < str.length) {
          const next = str.charCodeAt(i + 1);
          if (next >= 0xdc00 && next <= 0xdfff) {
            cp = 0x10000 + ((cp - 0xd800) << 10) + (next - 0xdc00);
            i++;
          }
        }
        if (cp < 0x80) {
          out.push(cp);
        } else if (cp < 0x800) {
          out.push(0xc0 | (cp >> 6), 0x80 | (cp & 0x3f));
        } else if (cp < 0x10000) {
          out.push(
            0xe0 | (cp >> 12),
            0x80 | ((cp >> 6) & 0x3f),
            0x80 | (cp & 0x3f)
          );
        } else {
          out.push(
            0xf0 | (cp >> 18),
            0x80 | ((cp >> 12) & 0x3f),
            0x80 | ((cp >> 6) & 0x3f),
            0x80 | (cp & 0x3f)
          );
        }
      }
      return new Uint8Array(out);
    }
  };
}

if (typeof globalThis.TextDecoder === "undefined") {
  globalThis.TextDecoder = class TextDecoder {
    constructor(encoding = "utf-8") {
      this.encoding = String(encoding).toLowerCase();
    }

    decode(input) {
      if (!input) return "";
      const bytes =
        input instanceof Uint8Array
          ? input
          : ArrayBuffer.isView(input)
            ? new Uint8Array(input.buffer, input.byteOffset, input.byteLength)
            : new Uint8Array(input);

      // UTF-16LE support (used occasionally by web-tree-sitter internals)
      if (this.encoding === "utf-16le" || this.encoding === "utf-16") {
        let result = "";
        for (let i = 0; i + 1 < bytes.length; i += 2) {
          result += String.fromCharCode(bytes[i] | (bytes[i + 1] << 8));
        }
        return result;
      }

      // Fast chunked UTF-8 decode
      const chunks = [];
      let chunk = "";
      let i = 0;
      const len = bytes.length;
      while (i < len) {
        const b0 = bytes[i++];
        if (b0 < 0x80) {
          chunk += String.fromCharCode(b0);
        } else if ((b0 & 0xe0) === 0xc0 && i < len) {
          const b1 = bytes[i++];
          chunk += String.fromCharCode(((b0 & 0x1f) << 6) | (b1 & 0x3f));
        } else if ((b0 & 0xf0) === 0xe0 && i + 1 < len) {
          const b1 = bytes[i++];
          const b2 = bytes[i++];
          chunk += String.fromCharCode(
            ((b0 & 0x0f) << 12) | ((b1 & 0x3f) << 6) | (b2 & 0x3f)
          );
        } else if ((b0 & 0xf8) === 0xf0 && i + 2 < len) {
          const b1 = bytes[i++];
          const b2 = bytes[i++];
          const b3 = bytes[i++];
          const cp =
            (((b0 & 0x07) << 18) |
              ((b1 & 0x3f) << 12) |
              ((b2 & 0x3f) << 6) |
              (b3 & 0x3f)) -
            0x10000;
          chunk += String.fromCharCode(
            0xd800 + (cp >> 10),
            0xdc00 + (cp & 0x3ff)
          );
        } else {
          chunk += "\ufffd";
        }
        if (chunk.length >= 4096) {
          chunks.push(chunk);
          chunk = "";
        }
      }
      if (chunk.length > 0) chunks.push(chunk);
      return chunks.join("");
    }
  };
}

if (typeof globalThis.console === "undefined") {
  const noop = () => {};
  globalThis.console = { log: noop, warn: noop, error: noop, info: noop, debug: noop };
}

if (typeof globalThis.performance === "undefined") {
  globalThis.performance = { now: () => Date.now() };
}

if (typeof globalThis.URL === "undefined") {
  globalThis.URL = class URL {
    constructor(input, base) {
      this.href = base ? `${String(base).replace(/\/[^/]*$/, "/")}${String(input)}` : String(input);
    }
    toString() {
      return this.href;
    }
  };
}
