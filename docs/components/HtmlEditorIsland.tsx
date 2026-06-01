import "@bambamboole/pdf-ua-template-builder/style.css";
import "./TemplateIslands.css";

import { type ReactNode } from "react";
import { resolveDocsApiUrl } from "./apiUrl";
import { usePdfUaTemplateBuilder } from "./usePdfUaTemplateBuilder";

interface HtmlEditorIslandProps {
  initialHtml: string;
}

export default function HtmlEditorIsland({ initialHtml }: HtmlEditorIslandProps) {
  const apiUrl = resolveDocsApiUrl(import.meta.env.PUBLIC_API_URL);
  const moduleState = usePdfUaTemplateBuilder();

  if (moduleState.status === "loading") {
    return <IslandMessage>Loading HTML editor...</IslandMessage>;
  }

  if (moduleState.status === "error") {
    return <IslandMessage>Unable to load HTML editor: {moduleState.message}</IslandMessage>;
  }

  const { HtmlCodeEditor, HtmlPreview, HtmlEditorProvider } = moduleState.module;

  return (
    <div className="pdfua-template-island not-content">
      <HtmlEditorProvider apiUrl={apiUrl} initialHtml={initialHtml}>
        <div className="pdfua-template-island__stack">
          <div className="pdfua-template-island__pane pdfua-template-island__editor">
            <HtmlCodeEditor />
          </div>
          <div className="pdfua-template-island__pane pdfua-template-island__preview">
            <HtmlPreview />
          </div>
        </div>
      </HtmlEditorProvider>
    </div>
  );
}

function IslandMessage({ children }: { children: ReactNode }) {
  return (
    <div className="pdfua-template-island not-content" role="status">
      <p className="pdfua-template-island__message">{children}</p>
    </div>
  );
}
