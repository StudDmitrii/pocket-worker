import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const assetsDir = path.resolve(__dirname, "..", "..", "app", "src", "main", "assets", "syntax");

// Simulate Android's androidx.javascriptengine bare V8 JavaScriptIsolate:
// NO window, NO document, NO fetch, NO TextEncoder/TextDecoder, NO process, NO Buffer, NO fs!
const namedData = new Map();

function provideNamedData(key, uint8Array) {
  // Store a clean ArrayBuffer copy (single-use, just like Android's provideNamedData)
  const copy = new Uint8Array(uint8Array.byteLength);
  copy.set(uint8Array);
  namedData.set(key, copy.buffer);
}

const sandboxGlobals = {
  WebAssembly,
  android: {
    consumeNamedDataAsArrayBuffer(key) {
      if (!namedData.has(key)) {
        return Promise.reject(new Error(`Named data '${key}' not found or already consumed`));
      }
      const buf = namedData.get(key);
      namedData.delete(key);
      return Promise.resolve(buf);
    },
  },
};

const context = vm.createContext(sandboxGlobals);

// Load the production bundle via bootstrap + provideNamedData (exact Android Binder-safe flow!)
const bootstrapJs = `
(function() {
  function decodeUtf8(buf) {
    const b = new Uint8Array(buf);
    const out = [];
    let chunk = "";
    let i = 0;
    const n = b.length;
    while (i < n) {
      const b0 = b[i++];
      if (b0 < 0x80) chunk += String.fromCharCode(b0);
      else if ((b0 & 0xe0) === 0xc0 && i < n) chunk += String.fromCharCode(((b0 & 0x1f) << 6) | (b[i++] & 0x3f));
      else if ((b0 & 0xf0) === 0xe0 && i + 1 < n) chunk += String.fromCharCode(((b0 & 0x0f) << 12) | ((b[i++] & 0x3f) << 6) | (b[i++] & 0x3f));
      else if ((b0 & 0xf8) === 0xf0 && i + 2 < n) {
        const cp = (((b0 & 0x07) << 18) | ((b[i++] & 0x3f) << 12) | ((b[i++] & 0x3f) << 6) | (b[i++] & 0x3f)) - 0x10000;
        chunk += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
      } else chunk += "\\ufffd";
      if (chunk.length >= 4096) { out.push(chunk); chunk = ""; }
    }
    if (chunk.length > 0) out.push(chunk);
    return out.join("");
  }
  globalThis.__pwLoadBundle = async function(key) {
    const buf = await android.consumeNamedDataAsArrayBuffer(key);
    const src = decodeUtf8(buf);
    (0, eval)(src);
    return "ok";
  };
  return "ok";
})();
`;
assert.equal(vm.runInContext(bootstrapJs, context, { filename: "bootstrap.js" }), "ok");
const bundleBytes = fs.readFileSync(path.join(assetsDir, "syntax-checker.bundle.js"));
provideNamedData("pw_bundle", bundleBytes);
assert.equal(await vm.runInContext(`globalThis.__pwLoadBundle("pw_bundle")`, context), "ok");

// Helper to initialize Tree-sitter & lazily load grammars via provideNamedData
let tsReady = false;
const loadedGrammars = new Set();

async function ensureGrammar(grammar) {
  if (!tsReady) {
    const rt = fs.readFileSync(path.join(assetsDir, "tree-sitter.wasm"));
    provideNamedData("ts-runtime", rt);
    assert.equal(
      await vm.runInContext(`globalThis.__pwSyntax.initTreeSitterFromNamedData("ts-runtime")`, context),
      "ok"
    );
    tsReady = true;
  }
  if (!loadedGrammars.has(grammar)) {
    const wasm = fs.readFileSync(path.join(assetsDir, `tree-sitter-${grammar}.wasm`));
    const key = `wasm_${grammar}`;
    provideNamedData(key, wasm);
    assert.equal(
      await vm.runInContext(
        `globalThis.__pwSyntax.loadGrammarFromNamedData(${JSON.stringify(grammar)}, ${JSON.stringify(key)})`,
        context
      ),
      "ok"
    );
    loadedGrammars.add(grammar);
  }
}

let seq = 0;
async function checkFile(filePath, code, requiredGrammar = null) {
  if (requiredGrammar) {
    await ensureGrammar(requiredGrammar);
  }
  const key = `src_${++seq}`;
  provideNamedData(key, Buffer.from(code, "utf8"));
  const meta = JSON.stringify({ filePath, grammar: requiredGrammar });
  const rawJson = await vm.runInContext(
    `globalThis.__pwSyntax.checkFromNamedData(${JSON.stringify(meta)}, ${JSON.stringify(key)})`,
    context
  );
  return JSON.parse(rawJson);
}

const tests = [
  // ---- JavaScript ----
  {
    name: "JS valid (ES2024 + JSX + Cyrillic)",
    file: "App.jsx",
    code: `
      import React from "react";
      export async function Greeting({ name = "Мир 🌍" }) {
        const value = name?.trim() ?? "Гость";
        return <div className="box">{value}</div>;
      }
    `,
    expectValid: true,
    expectEngine: "babel-parser",
  },
  {
    name: "JS broken (syntax error with balanced brackets!)",
    file: "broken.js",
    code: `
      function calc(a, b) {
        const x = a + * b;
        return x;
      }
    `,
    expectValid: false,
    expectEngine: "babel-parser",
  },
  // ---- TypeScript / TSX ----
  {
    name: "TS valid (generics, interfaces, satisfies)",
    file: "service.ts",
    code: `
      interface User<T> { id: number; data: T; }
      export const makeUser = <T,>(id: number, data: T): User<T> => ({ id, data });
      const check = 1 < 2 && 3 > 1;
    `,
    expectValid: true,
    expectEngine: "babel-parser",
  },
  {
    name: "TS broken (missing type after colon, balanced brackets)",
    file: "broken.ts",
    code: `
      export function greet(name: ): string {
        return "hi";
      }
    `,
    expectValid: false,
    expectEngine: "babel-parser",
  },
  {
    name: "TSX valid",
    file: "Button.tsx",
    code: `
      type Props = { label: string };
      export const Btn = ({ label }: Props) => <button>{label}</button>;
    `,
    expectValid: true,
    expectEngine: "babel-parser",
  },
  // ---- PHP ----
  {
    name: "PHP 8.x valid (enum, match, constructor promotion, Cyrillic)",
    file: "User.php",
    code: `<?php
      declare(strict_types=1);
      enum Role: string {
        case Admin = 'admin';
        case User = 'user';
      }
      class Account {
        public function __construct(
          public readonly string $name,
          public Role $role = Role::User,
        ) {}
        public function label(): string {
          return match($this->role) {
            Role::Admin => 'Админ',
            Role::User => 'Пользователь',
          };
        }
      }
    `,
    expectValid: true,
    expectEngine: "php-parser",
  },
  {
    name: "PHP valid mixed HTML + PHP",
    file: "view.phtml",
    code: `<!DOCTYPE html><html><body><h1><?= htmlspecialchars($title) ?></h1><?php if ($ok): ?><p>Да</p><?php endif; ?></body></html>`,
    expectValid: true,
    expectEngine: "php-parser",
  },
  {
    name: "PHP broken (missing semicolon — balanced brackets!)",
    file: "broken.php",
    code: `<?php
      function greet($name) {
        $msg = "Hello " . $name
        return $msg;
      }
    `,
    expectValid: false,
    expectEngine: "php-parser",
  },
  {
    name: "PHP broken (unclosed comment)",
    file: "unclosed.php",
    code: `<?php\n/* forgot to close\n$x = 1;\n`,
    expectValid: false,
    expectEngine: "php-parser",
  },
  // ---- Python ----
  {
    name: "Python valid (3.11 match/case, typing, f-string)",
    file: "main.py",
    grammar: "python",
    code: `
def handle(cmd: str) -> str:
    match cmd:
        case "ping":
            return f"pong: {cmd}"
        case _:
            return "неизвестно"
`,
    expectValid: true,
    expectEngine: "tree-sitter:python",
  },
  {
    name: "Python broken (missing colon after def — balanced brackets!)",
    file: "broken.py",
    grammar: "python",
    code: `
def compute(x, y)
    return x + y
`,
    expectValid: false,
    expectEngine: "tree-sitter:python",
  },
  // ---- C ----
  {
    name: "C valid",
    file: "main.c",
    grammar: "c",
    code: `
#include <stdio.h>
typedef struct { int x; int y; } Point;
int main(void) {
    Point p = {1, 2};
    printf("%d\\n", p.x + p.y);
    return 0;
}
`,
    expectValid: true,
    expectEngine: "tree-sitter:c",
  },
  {
    name: "C broken (missing semicolon — balanced brackets!)",
    file: "broken.c",
    grammar: "c",
    code: `
int add(int a, int b) {
    int sum = a + b
    return sum;
}
`,
    expectValid: false,
    expectEngine: "tree-sitter:c",
  },
  // ---- C++ ----
  {
    name: "C++ valid (template, namespace, lambda)",
    file: "engine.cpp",
    grammar: "cpp",
    code: `
#include <vector>
namespace core {
template<typename T>
T max_val(T a, T b) {
    auto cmp = [](const T& x, const T& y) { return x > y; };
    return cmp(a, b) ? a : b;
}
}
`,
    expectValid: true,
    expectEngine: "tree-sitter:cpp",
  },
  {
    name: "C++ broken (missing semicolon after statement)",
    file: "broken.cpp",
    grammar: "cpp",
    code: `
class Foo {
public:
    int get() {
        int a = 10
        return a;
    }
};
`,
    expectValid: false,
    expectEngine: "tree-sitter:cpp",
  },
  // ---- C# ----
  {
    name: "C# valid (record, async, LINQ)",
    file: "Service.cs",
    grammar: "c_sharp",
    code: `
using System;
namespace Demo {
    public record Person(string Name, int Age);
    public class App {
        public static void Main() {
            var p = new Person("Иван", 30);
            Console.WriteLine(p.Name);
        }
    }
}
`,
    expectValid: true,
    expectEngine: "tree-sitter:c_sharp",
  },
  {
    name: "C# broken (missing semicolon)",
    file: "Broken.cs",
    grammar: "c_sharp",
    code: `
class Broken {
    void Run() {
        int x = 1 + 2
        int y = x * 3;
    }
}
`,
    expectValid: false,
    expectEngine: "tree-sitter:c_sharp",
  },
  // ---- Java ----
  {
    name: "Java valid",
    file: "Main.java",
    grammar: "java",
    code: `
public class Main {
    public static int sum(int a, int b) {
        return a + b;
    }
}
`,
    expectValid: true,
    expectEngine: "tree-sitter:java",
  },
  {
    name: "Java broken (missing semicolon)",
    file: "Broken.java",
    grammar: "java",
    code: `
public class Broken {
    public static int sum(int a, int b) {
        int res = a + b
        return res;
    }
}
`,
    expectValid: false,
    expectEngine: "tree-sitter:java",
  },
  // ---- Kotlin ----
  {
    name: "Kotlin valid",
    file: "Demo.kt",
    grammar: "kotlin",
    code: `
data class Item(val id: Int, val name: String)
fun greet(item: Item): String = "Привет, \${item.name}"
`,
    expectValid: true,
    expectEngine: "tree-sitter:kotlin",
  },
  {
    name: "Kotlin broken (invalid fun syntax)",
    file: "Broken.kt",
    grammar: "kotlin",
    code: `
fun oops(x Int): String {
    return "bad"
}
`,
    expectValid: false,
    expectEngine: "tree-sitter:kotlin",
  },
  // ---- Go ----
  {
    name: "Go valid",
    file: "main.go",
    grammar: "go",
    code: `
package main
import "fmt"
func main() {
    ch := make(chan int, 1)
    ch <- 42
    fmt.Println(<-ch)
}
`,
    expectValid: true,
    expectEngine: "tree-sitter:go",
  },
  {
    name: "Go broken (missing func body / bad decl)",
    file: "broken.go",
    grammar: "go",
    code: `
package main
func broken(x int y int) {
    return
}
`,
    expectValid: false,
    expectEngine: "tree-sitter:go",
  },
  // ---- Rust ----
  {
    name: "Rust valid (lifetimes & match)",
    file: "lib.rs",
    grammar: "rust",
    code: `
pub fn first<'a>(s: &'a str) -> Option<char> {
    match s.chars().next() {
        Some(c) => Some(c),
        None => None,
    }
}
`,
    expectValid: true,
    expectEngine: "tree-sitter:rust",
  },
  {
    name: "Rust broken (missing semicolon)",
    file: "broken.rs",
    grammar: "rust",
    code: `
fn main() {
    let x = 10
    let y = x + 1;
}
`,
    expectValid: false,
    expectEngine: "tree-sitter:rust",
  },
  // ---- Bash ----
  {
    name: "Bash valid",
    file: "deploy.sh",
    grammar: "bash",
    code: `#!/usr/bin/env bash
set -euo pipefail
if [[ -f "README.md" ]]; then
  echo "OK"
fi
`,
    expectValid: true,
    expectEngine: "tree-sitter:bash",
  },
  {
    name: "Bash broken (missing fi)",
    file: "broken.sh",
    grammar: "bash",
    code: `#!/usr/bin/env bash
if [[ -f "README.md" ]]; then
  echo "missing fi"
`,
    expectValid: false,
    expectEngine: "tree-sitter:bash",
  },
  // ---- SQL ----
  {
    name: "SQL valid (PostgreSQL)",
    file: "schema.sql",
    code: `
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email TEXT NOT NULL UNIQUE
);
SELECT id, email FROM users WHERE id > 10 ORDER BY id DESC;
`,
    expectValid: true,
  },
  {
    name: "SQL valid (MySQL with backticks)",
    file: "mysql.sql",
    code: "SELECT `id`, `name` FROM `orders` WHERE `status` = 'paid' LIMIT 20;",
    expectValid: true,
  },
  {
    name: "SQL broken (typo SELEC ... FORM)",
    file: "broken.sql",
    code: `SELEC id FORM users WHERE;`,
    expectValid: false,
  },
  // ---- CSS ----
  {
    name: "CSS valid",
    file: "styles.css",
    code: `
:root { --primary: #2563eb; }
@media (max-width: 768px) {
  .card { display: grid; gap: 1rem; color: var(--primary); }
}
`,
    expectValid: true,
    expectEngine: "css-tree",
  },
  {
    name: "CSS broken (unclosed brace)",
    file: "broken.css",
    code: `.card { color: red; font-size: 14px;`,
    expectValid: false,
    expectEngine: "css-tree",
  },
  // ---- HTML ----
  {
    name: "HTML valid (void tags, script with < comparisons)",
    file: "index.html",
    code: `<!DOCTYPE html>
<html lang="ru">
<head><meta charset="utf-8"><title>Тест</title></head>
<body>
  <div class="app"><img src="a.png" alt="ok"><br><p>Привет</p></div>
  <script>if (a < b && b > c) console.log("ok");</script>
</body>
</html>`,
    expectValid: true,
    expectEngine: "parse5",
  },
  {
    name: "HTML broken (mismatched/unclosed tag + duplicate attr)",
    file: "broken.html",
    code: `<div id="a" id="b"><span>oops</div>`,
    expectValid: false,
    expectEngine: "parse5",
  },
  // ---- YAML ----
  {
    name: "YAML valid (multi-doc)",
    file: "config.yml",
    code: `---\napp:\n  name: PocketWorker\n  ports:\n    - 8080\n---\nsecond: true\n`,
    expectValid: true,
    expectEngine: "js-yaml",
  },
  {
    name: "YAML broken (bad indentation / mapping)",
    file: "broken.yaml",
    code: `app:\n  name: test\n bad: [1, 2\n`,
    expectValid: false,
    expectEngine: "js-yaml",
  },
  // ---- TOML ----
  {
    name: "TOML valid",
    file: "Cargo.toml",
    code: `[package]\nname = "demo"\nversion = "1.0.0"\ntags = ["a", "b"]\n`,
    expectValid: true,
    expectEngine: "smol-toml",
  },
  {
    name: "TOML broken (unclosed array)",
    file: "broken.toml",
    code: `[package]\ntags = ["a", "b"\n`,
    expectValid: false,
    expectEngine: "smol-toml",
  },
];

let passed = 0;
let failed = 0;

for (const t of tests) {
  const res = await checkFile(t.file, t.code, t.grammar || null);
  try {
    assert.equal(res.handled, true, `expected handled=true for ${t.file}, got ${JSON.stringify(res)}`);
    if (t.expectEngine) {
      assert.equal(res.engine, t.expectEngine, `engine mismatch for ${t.file}`);
    }
    const errCount = res.issues.filter((i) => i.severity === "error").length;
    if (t.expectValid) {
      assert.equal(errCount, 0, `expected 0 errors for valid ${t.file}, got: ${JSON.stringify(res.issues)}`);
    } else {
      assert.ok(errCount > 0, `expected >=1 error for broken ${t.file}, got 0 errors`);
    }
    const summary = errCount === 0
      ? "VALID"
      : `${errCount} err -> L${res.issues[0].line}:${res.issues[0].column} ${res.issues[0].message}`;
    console.log(`  [PASS] ${t.name.padEnd(56)} (${res.engine}) ${summary}`);
    passed++;
  } catch (e) {
    console.error(`  [FAIL] ${t.name}: ${e.message}`);
    failed++;
  }
}

console.log(`\nSandbox test summary: ${passed} passed, ${failed} failed (total ${tests.length})`);
if (failed > 0) process.exit(1);
