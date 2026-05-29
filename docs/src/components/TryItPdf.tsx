import { useEffect, useState } from "react";
import CodeMirror from "@uiw/react-codemirror";
import { html } from "@codemirror/lang-html";
import { json } from "@codemirror/lang-json";

const DEFAULT_API_URL = "http://localhost:8080";

type Mode = "html" | "template";

interface TryItPdfProps {
  mode: Mode;
  initialCode: string;
}

export default function TryItPdf({ mode, initialCode }: TryItPdfProps) {
  const apiUrl = import.meta.env.PUBLIC_API_URL ?? DEFAULT_API_URL;
  const [code, setCode] = useState(initialCode);
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    return () => {
      if (pdfUrl) URL.revokeObjectURL(pdfUrl);
    };
  }, [pdfUrl]);

  const extensions = mode === "html" ? [html()] : [json()];

  async function render() {
    setStatus(null);

    let endpoint: string;
    let body: string;
    if (mode === "html") {
      endpoint = `${apiUrl}/convert`;
      body = JSON.stringify({ html: code });
    } else {
      endpoint = `${apiUrl}/render/template`;
      let parsed: unknown;
      try {
        parsed = JSON.parse(code);
      } catch (err) {
        setStatus(`Invalid JSON: ${err instanceof Error ? err.message : String(err)}`);
        return;
      }
      body = JSON.stringify(parsed);
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
      setPdfUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return URL.createObjectURL(blob);
      });
    } catch (err) {
      setStatus(`Request failed: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <CodeMirror value={code} height="300px" extensions={extensions} onChange={(v) => setCode(v)} />
      <p>
        <button onClick={render} disabled={loading}>
          {loading ? "Rendering…" : "Render PDF"}
        </button>{" "}
        <small>API: {apiUrl}</small>
      </p>
      {status && <p role="alert">{status}</p>}
      {pdfUrl && (
        <>
          <iframe
            title="PDF preview"
            src={pdfUrl}
            style={{ width: "100%", height: "600px", border: "1px solid #ccc" }}
          />
          <p>
            <a href={pdfUrl} download="document.pdf">
              Download PDF
            </a>
          </p>
        </>
      )}
    </div>
  );
}
