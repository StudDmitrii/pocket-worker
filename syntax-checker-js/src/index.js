import "./polyfills.js";

import * as babelParser from "@babel/parser";
import * as csstree from "css-tree";
import * as yaml from "js-yaml";
import * as parse5 from "parse5";
import PhpEngine from "php-parser";
import * as smolToml from "smol-toml";
import { parse as parseSql } from "sql-parser-cst";
import { Parser as TsParser, Language as TsLanguage } from "web-tree-sitter";

const MAX_ISSUES = 50;

let tsInitialized = false;
let tsParserInstance = null;
const tsLanguages = new Map();

function makeIssue(line, column, severity, message, extra = {}) {
  const issue = {
    line: Math.max(1, Number(line) || 1),
    column: Math.max(1, Number(column) || 1),
    severity: severity === "warning" ? "warning" : "error",
    message: String(message || "Syntax error").trim(),
  };
  if (extra.endLine && extra.endLine >= issue.line) {
    issue.endLine = Number(extra.endLine);
  }
  if (extra.endColumn && extra.endColumn >= 1) {
    issue.endColumn = Number(extra.endColumn);
  }
  if (extra.code) {
    issue.code = String(extra.code);
  }
  return issue;
}

function dedupeAndCap(issues) {
  const seen = new Set();
  const out = [];
  for (const iss of issues) {
    const key = `${iss.line}:${iss.column}:${iss.severity}:${iss.message}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(iss);
    if (out.length >= MAX_ISSUES) break;
  }
  out.sort((a, b) => a.line - b.line || a.column - b.column);
  return out;
}

async function readNamedDataAsBytes(dataKey) {
  if (
    typeof globalThis.android === "undefined" ||
    typeof globalThis.android.consumeNamedDataAsArrayBuffer !== "function"
  ) {
    throw new Error("android.consumeNamedDataAsArrayBuffer is unavailable");
  }
  const buf = await globalThis.android.consumeNamedDataAsArrayBuffer(dataKey);
  return new Uint8Array(buf);
}

// ============================================================================
// 1. JavaScript / TypeScript / JSX / TSX (@babel/parser)
// ============================================================================

function checkJsTs(code, ext) {
  const isTs = ext === "ts" || ext === "mts" || ext === "cts" || ext === "tsx";
  const isJsx = ext === "jsx" || ext === "tsx" || ext === "js" || ext === "mjs" || ext === "cjs";

  const plugins = [
    "decorators-legacy",
    "importAttributes",
    "explicitResourceManagement",
    "classProperties",
    "classPrivateProperties",
    "classPrivateMethods",
    "topLevelAwait",
    "dynamicImport",
    "optionalChaining",
    "nullishCoalescingOperator",
    "numericSeparator",
    "bigInt",
  ];
  if (isTs) {
    plugins.push("typescript");
  }
  if (isJsx) {
    plugins.push("jsx");
  }

  const issues = [];
  const parseOptions = {
    sourceType: "unambiguous",
    allowAwaitOutsideFunction: true,
    allowSuperOutsideMethod: true,
    allowUndeclaredExports: true,
    errorRecovery: true,
    plugins,
  };

  try {
    const ast = babelParser.parse(code, parseOptions);
    if (Array.isArray(ast.errors)) {
      for (const err of ast.errors) {
        const line = err?.loc?.line ?? 1;
        const col = err?.loc?.column != null ? err.loc.column + 1 : 1;
        const cleanMsg = cleanBabelMessage(err?.message);
        issues.push(
          makeIssue(line, col, "error", cleanMsg, { code: err?.reasonCode })
        );
      }
    }
  } catch (err) {
    const line = err?.loc?.line ?? 1;
    const col = err?.loc?.column != null ? err.loc.column + 1 : 1;
    const cleanMsg = cleanBabelMessage(err?.message);
    issues.push(
      makeIssue(line, col, "error", cleanMsg, { code: err?.reasonCode })
    );
  }

  return {
    handled: true,
    engine: "babel-parser",
    issues: dedupeAndCap(issues),
  };
}

function cleanBabelMessage(msg) {
  if (!msg) return "JavaScript/TypeScript syntax error";
  // Babel appends " (line:col)" to error messages; strip duplicate suffix cleanly
  return String(msg).replace(/\s*\(\d+:\d+\)\s*$/, "").trim();
}

// ============================================================================
// 2. PHP (glayzzle/php-parser — PHP 8.x + mixed HTML/PHP)
// ============================================================================

const phpEngine = new PhpEngine({
  parser: {
    extractDoc: false,
    suppressErrors: true,
    version: 804,
  },
  ast: {
    withPositions: true,
  },
  lexer: {
    short_tags: true,
  },
});

function checkPhp(code, filePath) {
  const issues = [];
  try {
    const ast = phpEngine.parseCode(code, filePath || "file.php");
    if (ast && Array.isArray(ast.errors)) {
      for (const err of ast.errors) {
        const line = err?.loc?.start?.line ?? err?.line ?? 1;
        const col =
          err?.loc?.start?.column != null ? err.loc.start.column + 1 : 1;
        const msg = cleanPhpMessage(err?.message);
        issues.push(makeIssue(line, col, "error", msg));
      }
    }
  } catch (err) {
    const line = err?.lineNumber ?? 1;
    const col = err?.columnNumber != null ? err.columnNumber + 1 : 1;
    issues.push(makeIssue(line, col, "error", cleanPhpMessage(err?.message)));
  }

  // Catch unclosed strings/comments at EOF if lexer swallowed them silently
  if (issues.length === 0) {
    checkPhpUnclosedAtEof(code, issues);
  }

  return {
    handled: true,
    engine: "php-parser",
    issues: dedupeAndCap(issues),
  };
}

function cleanPhpMessage(msg) {
  if (!msg) return "PHP syntax error";
  return String(msg).replace(/^Parse Error\s*:\s*/i, "").trim();
}

function checkPhpUnclosedAtEof(code, issues) {
  // Quick scan inside <?php ... ?> blocks for unclosed /* comments
  let i = 0;
  let line = 1;
  let col = 1;
  const n = code.length;
  let inPhp = false;

  while (i < n) {
    if (!inPhp) {
      if (
        code[i] === "<" &&
        code[i + 1] === "?" &&
        (code.startsWith("<?php", i) ||
          code.startsWith("<?=", i) ||
          code[i + 2] === " " ||
          code[i + 2] === "\n" ||
          code[i + 2] === "\r" ||
          code[i + 2] === "\t")
      ) {
        inPhp = true;
        i += 2;
        col += 2;
        continue;
      }
      if (code[i] === "\n") {
        line++;
        col = 1;
      } else {
        col++;
      }
      i++;
      continue;
    }

    const c = code[i];
    const next = code[i + 1] || "";

    if (c === "?" && next === ">") {
      inPhp = false;
      i += 2;
      col += 2;
      continue;
    }
    if (c === "\n") {
      line++;
      col = 1;
      i++;
      continue;
    }
    if ((c === "/" && next === "/") || c === "#") {
      while (i < n && code[i] !== "\n") {
        if (code[i] === "?" && code[i + 1] === ">") break;
        i++;
        col++;
      }
      continue;
    }
    if (c === "/" && next === "*") {
      const sLine = line;
      const sCol = col;
      i += 2;
      col += 2;
      let closed = false;
      while (i < n) {
        if (code[i] === "*" && code[i + 1] === "/") {
          i += 2;
          col += 2;
          closed = true;
          break;
        }
        if (code[i] === "\n") {
          line++;
          col = 1;
        } else {
          col++;
        }
        i++;
      }
      if (!closed) {
        issues.push(
          makeIssue(sLine, sCol, "error", "Unterminated block comment '/*'")
        );
      }
      continue;
    }
    if (c === "'" || c === '"') {
      const q = c;
      const sLine = line;
      const sCol = col;
      i++;
      col++;
      let closed = false;
      while (i < n) {
        if (code[i] === "\\" && i + 1 < n) {
          if (code[i + 1] === "\n") {
            line++;
            col = 1;
          } else {
            col += 2;
          }
          i += 2;
          continue;
        }
        if (code[i] === q) {
          i++;
          col++;
          closed = true;
          break;
        }
        if (code[i] === "\n") {
          line++;
          col = 1;
        } else {
          col++;
        }
        i++;
      }
      if (!closed) {
        issues.push(
          makeIssue(sLine, sCol, "error", `Unterminated string literal ${q}`)
        );
      }
      continue;
    }
    i++;
    col++;
  }
}

// ============================================================================
// 3. CSS (css-tree + EOF unclosed block/comment/string checks)
// ============================================================================

function checkCss(code) {
  const issues = [];
  try {
    csstree.parse(code, {
      positions: true,
      onParseError(err) {
        issues.push(
          makeIssue(
            err?.line ?? 1,
            err?.column ?? 1,
            "error",
            err?.formattedMessage || err?.message || "CSS syntax error",
            { code: err?.name }
          )
        );
      },
    });
  } catch (err) {
    issues.push(
      makeIssue(
        err?.line ?? 1,
        err?.column ?? 1,
        "error",
        err?.message || "CSS syntax error"
      )
    );
  }

  // CSS Syntax Level 3 auto-closes EOF blocks/strings/comments; check them explicitly
  checkCssStructuralBalance(code, issues);

  return {
    handled: true,
    engine: "css-tree",
    issues: dedupeAndCap(issues),
  };
}

function checkCssStructuralBalance(code, issues) {
  const stack = [];
  let line = 1;
  let col = 1;
  let i = 0;
  const n = code.length;

  while (i < n) {
    const c = code[i];
    const next = code[i + 1] || "";

    if (c === "\n") {
      line++;
      col = 1;
      i++;
      continue;
    }
    if (c === "/" && next === "*") {
      const sLine = line;
      const sCol = col;
      i += 2;
      col += 2;
      let closed = false;
      while (i < n) {
        if (code[i] === "*" && code[i + 1] === "/") {
          i += 2;
          col += 2;
          closed = true;
          break;
        }
        if (code[i] === "\n") {
          line++;
          col = 1;
        } else {
          col++;
        }
        i++;
      }
      if (!closed) {
        issues.push(
          makeIssue(sLine, sCol, "error", "Unterminated CSS comment '/*'")
        );
      }
      continue;
    }
    if (c === '"' || c === "'") {
      const q = c;
      const sLine = line;
      const sCol = col;
      i++;
      col++;
      let closed = false;
      while (i < n && code[i] !== "\n") {
        if (code[i] === "\\" && i + 1 < n) {
          i += 2;
          col += 2;
          continue;
        }
        if (code[i] === q) {
          i++;
          col++;
          closed = true;
          break;
        }
        i++;
        col++;
      }
      if (!closed) {
        issues.push(
          makeIssue(sLine, sCol, "error", `Unterminated CSS string ${q}`)
        );
      }
      continue;
    }
    if (c === "{" || c === "(" || c === "[") {
      stack.push({ ch: c, line, col });
    } else if (c === "}" || c === ")" || c === "]") {
      const expected = c === "}" ? "{" : c === ")" ? "(" : "[";
      if (stack.length === 0) {
        issues.push(
          makeIssue(line, col, "error", `Unexpected closing '${c}' in CSS`)
        );
      } else {
        const top = stack.pop();
        if (top.ch !== expected) {
          issues.push(
            makeIssue(
              line,
              col,
              "error",
              `Mismatched '${c}' in CSS (expected closing for '${top.ch}' at line ${top.line})`
            )
          );
        }
      }
    }
    i++;
    col++;
  }

  while (stack.length > 0) {
    const unclosed = stack.pop();
    issues.push(
      makeIssue(
        unclosed.line,
        unclosed.col,
        "error",
        `Unclosed '${unclosed.ch}' in CSS`
      )
    );
  }
}

// ============================================================================
// 4. HTML (parse5 WHATWG parser + void-aware tag balance)
// ============================================================================

const HTML_ERROR_CODES = new Set([
  "abrupt-closing-of-empty-comment",
  "duplicate-attribute",
  "end-tag-with-attributes",
  "end-tag-with-trailing-solidus",
  "eof-before-tag-name",
  "eof-in-cdata",
  "eof-in-comment",
  "eof-in-script-html-comment-like-text",
  "eof-in-tag",
  "incorrectly-closed-comment",
  "invalid-first-character-of-tag-name",
  "missing-attribute-value",
  "missing-end-tag-name",
  "missing-whitespace-between-attributes",
  "nested-comment",
  "unexpected-character-in-attribute-name",
  "unexpected-character-in-unquoted-attribute-value",
  "unexpected-equals-sign-before-attribute-name",
  "unexpected-question-mark-instead-of-tag-name",
  "unexpected-solidus-in-tag",
]);

const HTML_VOID_ELEMENTS = new Set([
  "area",
  "base",
  "br",
  "col",
  "embed",
  "hr",
  "img",
  "input",
  "link",
  "meta",
  "param",
  "source",
  "track",
  "wbr",
]);

const HTML_OPTIONAL_END_TAGS = new Set([
  "p",
  "li",
  "dt",
  "dd",
  "option",
  "optgroup",
  "tr",
  "td",
  "th",
  "thead",
  "tbody",
  "tfoot",
  "colgroup",
  "html",
  "head",
  "body",
]);

function checkHtml(code) {
  const issues = [];
  const hasServerTemplates = /<\?php|\{\{|\{%|<%/.test(code);

  try {
    parse5.parse(code, {
      sourceCodeLocationInfo: true,
      onParseError(err) {
        if (!err || !err.code) return;
        if (hasServerTemplates && err.code === "unexpected-question-mark-instead-of-tag-name") {
          return;
        }
        if (HTML_ERROR_CODES.has(err.code)) {
          issues.push(
            makeIssue(
              err.startLine ?? 1,
              err.startCol ?? 1,
              "error",
              formatHtmlError(err.code),
              {
                endLine: err.endLine,
                endColumn: err.endCol,
                code: err.code,
              }
            )
          );
        }
      },
    });
  } catch (err) {
    issues.push(
      makeIssue(1, 1, "error", err?.message || "HTML parse error")
    );
  }

  // Catch unclosed / mismatched tags (skip if template syntax like Jinja/Blade/PHP is present)
  if (!hasServerTemplates) {
    checkHtmlTagBalance(code, issues);
  }

  return {
    handled: true,
    engine: "parse5",
    issues: dedupeAndCap(issues),
  };
}

function formatHtmlError(code) {
  return "HTML syntax error: " + String(code).replace(/-/g, " ");
}

function checkHtmlTagBalance(code, issues) {
  const stack = [];
  const n = code.length;
  let i = 0;
  let line = 1;
  let col = 1;

  const advance = (count) => {
    for (let k = 0; k < count && i < n; k++) {
      if (code[i] === "\n") {
        line++;
        col = 1;
      } else {
        col++;
      }
      i++;
    }
  };

  while (i < n) {
    if (code[i] !== "<") {
      advance(1);
      continue;
    }

    // Comments <!-- ... -->
    if (code.startsWith("<!--", i)) {
      const end = code.indexOf("-->", i + 4);
      if (end === -1) return; // parse5 already reports eof-in-comment
      advance(end + 3 - i);
      continue;
    }
    // DOCTYPE / CDATA / processing instruction
    if (code.startsWith("<!", i) || code.startsWith("<?", i)) {
      const end = code.indexOf(">", i + 2);
      if (end === -1) return;
      advance(end + 1 - i);
      continue;
    }

    const startLine = line;
    const startCol = col;
    advance(1); // '<'

    let isClosing = false;
    if (i < n && code[i] === "/") {
      isClosing = true;
      advance(1);
    }

    const nameStart = i;
    while (i < n && /[A-Za-z0-9:_-]/.test(code[i])) {
      advance(1);
    }
    const rawTag = code.slice(nameStart, i);
    if (!rawTag) continue;
    const tag = rawTag.toLowerCase();

    // Scan until '>' respecting quoted attributes
    let selfClosing = false;
    while (i < n && code[i] !== ">") {
      if (code[i] === '"' || code[i] === "'") {
        const q = code[i];
        advance(1);
        while (i < n && code[i] !== q) advance(1);
        if (i < n) advance(1);
        continue;
      }
      if (code[i] === "/" && code[i + 1] === ">") {
        selfClosing = true;
        advance(2);
        break;
      }
      advance(1);
    }
    if (!selfClosing && i < n && code[i] === ">") {
      advance(1);
    }

    // Skip raw text content of <script>, <style>, <textarea>
    if (!isClosing && !selfClosing && (tag === "script" || tag === "style" || tag === "textarea")) {
      const closeRe = new RegExp(`</${tag}\\s*>`, "i");
      const rest = code.slice(i);
      const m = closeRe.exec(rest);
      if (!m) {
        issues.push(
          makeIssue(startLine, startCol, "error", `Unclosed <${tag}> tag`)
        );
        return;
      }
      advance(m.index + m[0].length);
      continue;
    }

    if (HTML_VOID_ELEMENTS.has(tag) || selfClosing) continue;

    if (!isClosing) {
      stack.push({ tag, line: startLine, col: startCol });
    } else {
      // Find matching opening tag on stack
      let matchIdx = -1;
      for (let s = stack.length - 1; s >= 0; s--) {
        if (stack[s].tag === tag) {
          matchIdx = s;
          break;
        }
      }
      if (matchIdx === -1) {
        issues.push(
          makeIssue(
            startLine,
            startCol,
            "error",
            `Unexpected closing tag </${tag}> with no matching opening tag`
          )
        );
      } else {
        // Any non-optional tags above matchIdx were left unclosed
        while (stack.length - 1 > matchIdx) {
          const popped = stack.pop();
          if (!HTML_OPTIONAL_END_TAGS.has(popped.tag)) {
            issues.push(
              makeIssue(
                popped.line,
                popped.col,
                "error",
                `Unclosed <${popped.tag}> tag before </${tag}> (line ${startLine})`
              )
            );
          }
        }
        stack.pop();
      }
    }
  }

  for (const unclosed of stack) {
    if (!HTML_OPTIONAL_END_TAGS.has(unclosed.tag)) {
      issues.push(
        makeIssue(
          unclosed.line,
          unclosed.col,
          "error",
          `Unclosed <${unclosed.tag}> tag`
        )
      );
    }
  }
}

// ============================================================================
// 5. YAML (js-yaml)
// ============================================================================

function checkYaml(code) {
  const issues = [];
  try {
    yaml.loadAll(code, () => {});
  } catch (err) {
    const line = err?.mark?.line != null ? err.mark.line + 1 : 1;
    const col = err?.mark?.column != null ? err.mark.column + 1 : 1;
    const msg = err?.reason || err?.message || "YAML syntax error";
    issues.push(makeIssue(line, col, "error", msg));
  }
  return {
    handled: true,
    engine: "js-yaml",
    issues: dedupeAndCap(issues),
  };
}

// ============================================================================
// 6. TOML (smol-toml)
// ============================================================================

function checkToml(code) {
  const issues = [];
  try {
    smolToml.parse(code);
  } catch (err) {
    const line = err?.line ?? 1;
    const col = err?.column ?? 1;
    const firstLineMsg = String(err?.message || "TOML syntax error")
      .split("\n")[0]
      .trim();
    issues.push(makeIssue(line, col, "error", firstLineMsg));
  }
  return {
    handled: true,
    engine: "smol-toml",
    issues: dedupeAndCap(issues),
  };
}

// ============================================================================
// 7. SQL (sql-parser-cst — multi-dialect: postgresql, mysql, sqlite, mariadb)
// ============================================================================

function checkSql(code) {
  if (!code.trim()) {
    return { handled: true, engine: "sql-parser-cst", issues: [] };
  }

  // Pick most likely dialect order based on common dialect-specific syntax
  let dialects = ["postgresql", "mysql", "sqlite", "mariadb"];
  if (/`[^`]+`|\bAUTO_INCREMENT\b|\bENGINE\s*=/i.test(code)) {
    dialects = ["mysql", "mariadb", "sqlite", "postgresql"];
  } else if (/\bAUTOINCREMENT\b|\bPRAGMA\b|\bWITHOUT\s+ROWID\b/i.test(code)) {
    dialects = ["sqlite", "postgresql", "mysql", "mariadb"];
  }

  let bestErr = null;
  let bestScore = -1;

  for (const dialect of dialects) {
    try {
      parseSql(code, { dialect });
      return {
        handled: true,
        engine: `sql-parser-cst:${dialect}`,
        issues: [],
      };
    } catch (err) {
      const line = err?.location?.start?.line ?? 1;
      const col = err?.location?.start?.column ?? 1;
      const score = line * 100000 + col;
      if (score > bestScore) {
        bestScore = score;
        bestErr = { err, dialect, line, col };
      }
    }
  }

  const issues = [];
  if (bestErr) {
    const rawMsg = String(bestErr.err?.message || "SQL syntax error");
    // Strip the verbose ASCII snippet block if present; keep the crisp error line
    const firstLine = rawMsg.split("\n")[0].trim();
    issues.push(
      makeIssue(
        bestErr.line,
        bestErr.col,
        "error",
        firstLine || "SQL syntax error"
      )
    );
  }

  return {
    handled: true,
    engine: "sql-parser-cst",
    issues: dedupeAndCap(issues),
  };
}

// ============================================================================
// 8. Tree-sitter WASM (Python, C, C++, C#, Java, Kotlin, Go, Rust, Bash)
// ============================================================================

async function initTreeSitterFromNamedData(runtimeDataKey) {
  if (tsInitialized) return "ok";
  const wasmBytes = await readNamedDataAsBytes(runtimeDataKey);
  await initTreeSitterWithBytes(wasmBytes);
  return "ok";
}

async function initTreeSitterWithBytes(wasmBytes) {
  if (tsInitialized) return "ok";
  await TsParser.init({
    wasmBinary: wasmBytes,
    locateFile: (file) => file,
  });
  tsParserInstance = new TsParser();
  tsInitialized = true;
  return "ok";
}

async function loadGrammarFromNamedData(grammarName, dataKey) {
  if (tsLanguages.has(grammarName)) return "ok";
  const wasmBytes = await readNamedDataAsBytes(dataKey);
  await loadGrammarWithBytes(grammarName, wasmBytes);
  return "ok";
}

async function loadGrammarWithBytes(grammarName, wasmBytes) {
  if (tsLanguages.has(grammarName)) return "ok";
  if (!tsInitialized) {
    throw new Error("Tree-sitter runtime not initialized");
  }
  const lang = await TsLanguage.load(wasmBytes);
  tsLanguages.set(grammarName, lang);
  return "ok";
}

function checkTreeSitter(code, grammarName) {
  if (!tsInitialized || !tsParserInstance) {
    return { handled: false, reason: "tree-sitter not initialized", issues: [] };
  }
  const language = tsLanguages.get(grammarName);
  if (!language) {
    return {
      handled: false,
      reason: `grammar '${grammarName}' not loaded`,
      issues: [],
    };
  }

  tsParserInstance.setLanguage(language);
  const tree = tsParserInstance.parse(code);
  try {
    const root = tree.rootNode;
    if (!root.hasError) {
      return {
        handled: true,
        engine: `tree-sitter:${grammarName}`,
        issues: [],
      };
    }

    const issues = [];
    collectTreeSitterErrors(root, issues);

    // Safety net: if root.hasError was true but recursion somehow yielded 0 issues
    if (issues.length === 0) {
      issues.push(
        makeIssue(
          root.startPosition.row + 1,
          root.startPosition.column + 1,
          "error",
          `Syntax error in ${grammarName} file`
        )
      );
    }

    return {
      handled: true,
      engine: `tree-sitter:${grammarName}`,
      issues: dedupeAndCap(issues),
    };
  } finally {
    tree.delete();
  }
}

function collectTreeSitterErrors(node, issues) {
  if (issues.length >= MAX_ISSUES) return;
  if (!node.hasError && !node.isMissing && node.type !== "ERROR") return;

  if (node.isMissing) {
    const start = node.startPosition;
    const end = node.endPosition;
    const parentCtx = formatParentContext(node.parent);
    const expected = node.type && node.type !== "ERROR" ? `'${node.type}'` : "token";
    issues.push(
      makeIssue(
        start.row + 1,
        start.column + 1,
        "error",
        `Missing expected ${expected}${parentCtx}`,
        {
          endLine: end.row + 1,
          endColumn: end.column + 1,
          code: "MISSING_TOKEN",
        }
      )
    );
    return;
  }

  if (node.type === "ERROR") {
    // Check if there are more specific nested missing/ERROR nodes; if none, report this ERROR node
    let hasSpecificChildError = false;
    for (let i = 0; i < node.childCount; i++) {
      const child = node.child(i);
      if (child && (child.type === "ERROR" || child.isMissing)) {
        hasSpecificChildError = true;
        break;
      }
    }
    if (hasSpecificChildError) {
      for (let i = 0; i < node.childCount; i++) {
        const child = node.child(i);
        if (child) collectTreeSitterErrors(child, issues);
      }
      return;
    }

    const start = node.startPosition;
    const end = node.endPosition;
    const rawSnippet = (node.text || "").replace(/\s+/g, " ").trim();
    const preview =
      rawSnippet.length > 48 ? rawSnippet.slice(0, 48) + "…" : rawSnippet;
    const parentCtx = formatParentContext(node.parent);
    const message = preview
      ? `Syntax error${parentCtx} near '${preview}'`
      : `Syntax error${parentCtx}`;

    issues.push(
      makeIssue(start.row + 1, start.column + 1, "error", message, {
        endLine: end.row + 1,
        endColumn: end.column + 1,
        code: "SYNTAX_ERROR",
      })
    );
    return;
  }

  for (let i = 0; i < node.childCount; i++) {
    const child = node.child(i);
    if (child && (child.hasError || child.isMissing)) {
      collectTreeSitterErrors(child, issues);
    }
  }
}

function formatParentContext(parent) {
  if (!parent || !parent.type) return "";
  const ignored = new Set([
    "ERROR",
    "program",
    "module",
    "source_file",
    "translation_unit",
    "compilation_unit",
    "block",
    "chunk",
  ]);
  if (ignored.has(parent.type)) return "";
  return ` in ${parent.type.replace(/_/g, " ")}`;
}

// ============================================================================
// Dispatcher
// ============================================================================

const EXT_TO_TS_GRAMMAR = {
  py: "python",
  pyw: "python",
  pyi: "python",
  c: "c",
  h: "c",
  cpp: "cpp",
  cc: "cpp",
  cxx: "cpp",
  hpp: "cpp",
  hh: "cpp",
  hxx: "cpp",
  cs: "c_sharp",
  java: "java",
  kt: "kotlin",
  kts: "kotlin",
  go: "go",
  rs: "rust",
  sh: "bash",
  bash: "bash",
  zsh: "bash",
};

function extractExtension(filePath) {
  const base = String(filePath || "").split(/[\\/]/).pop() || "";
  const lower = base.toLowerCase();
  if (lower === "gradlew" || lower === ".bashrc" || lower === ".zshrc") {
    return "sh";
  }
  const dot = lower.lastIndexOf(".");
  return dot >= 0 ? lower.slice(dot + 1) : "";
}

function checkCode(rawCode, filePath, explicitGrammar) {
  const code = typeof rawCode === "string" && rawCode.charCodeAt(0) === 0xfeff
    ? rawCode.slice(1)
    : String(rawCode || "");
  const ext = extractExtension(filePath);

  if (explicitGrammar) {
    return checkTreeSitter(code, explicitGrammar);
  }

  switch (ext) {
    case "js":
    case "mjs":
    case "cjs":
    case "jsx":
    case "ts":
    case "mts":
    case "cts":
    case "tsx":
      return checkJsTs(code, ext);

    case "php":
    case "phtml":
      return checkPhp(code, filePath);

    case "css":
      return checkCss(code);

    case "html":
    case "htm":
      return checkHtml(code);

    case "yml":
    case "yaml":
      return checkYaml(code);

    case "toml":
      return checkToml(code);

    case "sql":
    case "ddl":
    case "dml":
      return checkSql(code);

    default: {
      const tsGrammar = EXT_TO_TS_GRAMMAR[ext];
      if (tsGrammar) {
        return checkTreeSitter(code, tsGrammar);
      }
      return { handled: false, reason: `unsupported extension '${ext}'`, issues: [] };
    }
  }
}

async function checkFromNamedData(metaJson, sourceDataKey) {
  const meta = JSON.parse(metaJson);
  const bytes = await readNamedDataAsBytes(sourceDataKey);
  const code = new TextDecoder("utf-8").decode(bytes);
  const result = checkCode(code, meta.filePath || "", meta.grammar || null);
  return JSON.stringify(result);
}

function checkFromJson(payloadJson) {
  const payload = JSON.parse(payloadJson);
  const result = checkCode(
    payload.code || "",
    payload.filePath || "",
    payload.grammar || null
  );
  return JSON.stringify(result);
}

globalThis.__pwSyntax = {
  version: "1.0.0",
  initTreeSitterFromNamedData,
  initTreeSitterWithBytes,
  loadGrammarFromNamedData,
  loadGrammarWithBytes,
  checkFromNamedData,
  checkFromJson,
  checkCode,
};
