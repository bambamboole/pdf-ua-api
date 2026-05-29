import { useEffect, useState } from "react";
import CodeMirror, { EditorView } from "@uiw/react-codemirror";
import { html } from "@codemirror/lang-html";
import { json } from "@codemirror/lang-json";

const DEFAULT_API_URL = "http://localhost:8080";

// Explicit, integer line-height shared by content and gutter keeps the line
// numbers aligned while scrolling, and a monospace stack overrides the inherited
// prose styles from the docs theme.
const editorTheme = EditorView.theme({
  "&": { fontSize: "13px" },
  ".cm-scroller": {
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
    lineHeight: "1rem",
  },
});

type Mode = "html" | "template" | "image";

interface TryItPdfProps {
  mode: Mode;
  initialCode: string;
}

/** Tracks Starlight's resolved color scheme via the `data-theme` attribute. */
function useStarlightDark(): boolean {
  const [isDark, setIsDark] = useState(false);
  useEffect(() => {
    const root = document.documentElement;
    const read = () => setIsDark(root.dataset.theme === "dark");
    read();
    const observer = new MutationObserver(read);
    observer.observe(root, { attributes: true, attributeFilter: ["data-theme"] });
    return () => observer.disconnect();
  }, []);
  return isDark;
}

export default function TryItPdf({ mode, initialCode }: TryItPdfProps) {
  const apiUrl = import.meta.env.PUBLIC_API_URL ?? DEFAULT_API_URL;
  const isDark = useStarlightDark();
  const [code, setCode] = useState(initialCode);
  const [resultUrl, setResultUrl] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    return () => {
      if (resultUrl) URL.revokeObjectURL(resultUrl);
    };
  }, [resultUrl]);

  const extensions = [mode === "template" ? json() : html(), editorTheme];

  async function render() {
    setStatus(null);

    let endpoint: string;
    let body: string;
    if (mode === "template") {
      endpoint = `${apiUrl}/render/template`;
      let parsed: unknown;
      try {
        parsed = JSON.parse(code);
      } catch (err) {
        setStatus(`Invalid JSON: ${err instanceof Error ? err.message : String(err)}`);
        return;
      }
      body = JSON.stringify(parsed);
    } else if (mode === "image") {
      endpoint = `${apiUrl}/render`;
      body = JSON.stringify({ html: code, format: "png" });
    } else {
      endpoint = `${apiUrl}/convert`;
      body = JSON.stringify({ html: code });
    }

    setLoading(true);
    try {
      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
      });
      if (!res.ok) {
        const text = await res.text();
        setStatus(`Error ${res.status}: ${text || res.statusText}`);
        return;
      }
      const blob = await res.blob();
      setResultUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return URL.createObjectURL(blob);
      });
    } catch (err) {
      setStatus(`Request failed: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setLoading(false);
    }
  }

  const actionLabel = mode === "image" ? "Render image" : "Render PDF";

  return (
    <div>
      <CodeMirror
        value={code}
        height="420px"
        theme={isDark ? "dark" : "light"}
        extensions={extensions}
        basicSetup={{ foldGutter: false }}
        onChange={(v) => setCode(v)}
      />
      <p>
        <button onClick={render} disabled={loading}>
          {loading ? "Rendering…" : actionLabel}
        </button>{" "}
        <small>API: {apiUrl}</small>
      </p>
      {status && <p role="alert">{status}</p>}
      {resultUrl &&
        (mode === "image" ? (
          <>
            <img
              src={resultUrl}
              alt="Rendered output"
              style={{ maxWidth: "100%", border: "1px solid #ccc" }}
            />
            <p>
              <a href={resultUrl} download="image.png">
                Download image
              </a>
            </p>
          </>
        ) : (
          <>
            <iframe
              title="PDF preview"
              src={resultUrl}
              style={{ width: "100%", height: "600px", border: "1px solid #ccc" }}
            />
            <p>
              <a href={resultUrl} download="document.pdf">
                Download PDF
              </a>
            </p>
          </>
        ))}
    </div>
  );
}
