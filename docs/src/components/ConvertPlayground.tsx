import { useState } from "react";

const DEFAULT_API_URL = "http://localhost:8080";
const SAMPLE_HTML = `<!doctype html>
<html lang="en">
  <head><title>Sample</title></head>
  <body>
    <h1>Hello PDF</h1>
    <p>Edit this HTML and generate a PDF.</p>
  </body>
</html>`;

export default function ConvertPlayground() {
  const apiUrl = import.meta.env.PUBLIC_API_URL ?? DEFAULT_API_URL;
  const [html, setHtml] = useState(SAMPLE_HTML);
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function generate() {
    setLoading(true);
    setStatus(null);
    try {
      const res = await fetch(`${apiUrl}/convert`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ html }),
      });
      if (!res.ok) {
        setStatus(`Error: ${res.status} ${res.statusText}`);
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
      <textarea
        value={html}
        onChange={(e) => setHtml(e.target.value)}
        rows={12}
        style={{ width: "100%", fontFamily: "monospace" }}
      />
      <p>
        <button onClick={generate} disabled={loading}>
          {loading ? "Generating…" : "Generate PDF"}
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
