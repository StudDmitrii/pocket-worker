import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import { fileURLToPath } from "node:url";
import * as esbuild from "esbuild";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
const outDir = path.resolve(rootDir, "..", "app", "src", "main", "assets", "syntax");

fs.mkdirSync(outDir, { recursive: true });

// Emscripten inside web-tree-sitter conditionally calls require("fs") / require("path")
// only when running under Node.js. Stub Node built-ins so the browser/V8 bundle is 100% self-contained.
const stubNodeBuiltinsPlugin = {
  name: "stub-node-builtins",
  setup(build) {
    build.onResolve({ filter: /^(node:)?(fs|fs\/promises|path|url|module|crypto)$/ }, (args) => {
      return { path: args.path, namespace: "node-builtin-stub" };
    });
    build.onLoad({ filter: /.*/, namespace: "node-builtin-stub" }, () => ({
      contents: "export default {}; export const readFile = () => { throw new Error('fs disabled in sandbox'); };",
      loader: "js",
    }));
  },
};

const bundleOutPath = path.join(outDir, "syntax-checker.bundle.js");

await esbuild.build({
  entryPoints: [path.join(rootDir, "src", "index.js")],
  outfile: bundleOutPath,
  bundle: true,
  minify: true,
  format: "iife",
  platform: "browser",
  target: ["es2022"],
  define: {
    "process.env.NODE_ENV": '"production"',
  },
  plugins: [stubNodeBuiltinsPlugin],
  logLevel: "info",
});

// Copy web-tree-sitter runtime wasm + language grammars
const wasmCopies = [
  {
    src: path.join(rootDir, "node_modules", "web-tree-sitter", "tree-sitter.wasm"),
    dst: path.join(outDir, "tree-sitter.wasm"),
  },
];

const grammars = [
  "python",
  "c",
  "cpp",
  "c_sharp",
  "java",
  "kotlin",
  "go",
  "rust",
  "bash",
];

for (const g of grammars) {
  wasmCopies.push({
    src: path.join(rootDir, "node_modules", "tree-sitter-wasms", "out", `tree-sitter-${g}.wasm`),
    dst: path.join(outDir, `tree-sitter-${g}.wasm`),
  });
}

for (const item of wasmCopies) {
  fs.copyFileSync(item.src, item.dst);
}

// Report exact raw & DEFLATE (APK-compressed) sizes
const generatedFiles = [bundleOutPath, ...wasmCopies.map((w) => w.dst)];
let totalRaw = 0;
let totalZip = 0;

console.log("\nGenerated PocketWorker Android syntax assets:");
console.log("─".repeat(64));
for (const file of generatedFiles) {
  const buf = fs.readFileSync(file);
  const zip = zlib.deflateSync(buf, { level: 9 });
  totalRaw += buf.length;
  totalZip += zip.length;
  console.log(
    `${path.basename(file).padEnd(32)} raw=${(buf.length / 1024).toFixed(1).padStart(7)} KB   apk≈${(zip.length / 1024).toFixed(1).padStart(6)} KB`
  );
}
console.log("─".repeat(64));
console.log(
  `${"TOTAL".padEnd(32)} raw=${(totalRaw / 1024 / 1024).toFixed(2).padStart(6)} MB   apk≈${(totalZip / 1024 / 1024).toFixed(2).padStart(5)} MB`
);
