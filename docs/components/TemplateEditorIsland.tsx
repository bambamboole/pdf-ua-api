import "@bambamboole/pdf-ua-template-builder/style.css";
import "./TemplateIslands.css";

import { useMemo, type ReactNode } from "react";
import { parseTemplateRequest } from "./templateRequest";
import { usePdfUaTemplateBuilder } from "./usePdfUaTemplateBuilder";

const DEFAULT_API_URL = "https://pdf-ua-api.c1.bambamboole.com";

interface TemplateEditorIslandProps {
  initialCode: string;
}

export default function TemplateEditorIsland({ initialCode }: TemplateEditorIslandProps) {
  const apiUrl = import.meta.env.PUBLIC_API_URL ?? DEFAULT_API_URL;
  const parsed = useMemo(() => parseSafely(initialCode), [initialCode]);
  const moduleState = usePdfUaTemplateBuilder();

  if (!parsed.ok) {
    return (
      <div className="pdfua-template-island not-content" role="alert">
        <p className="pdfua-template-island__error">{parsed.message}</p>
      </div>
    );
  }

  if (moduleState.status === "loading") {
    return <IslandMessage>Loading template editor...</IslandMessage>;
  }

  if (moduleState.status === "error") {
    return <IslandMessage>Unable to load template editor: {moduleState.message}</IslandMessage>;
  }

  const { CodeEditor, Preview, TemplateEditorProvider } = moduleState.module;

  return (
    <div className="pdfua-template-island not-content">
      <TemplateEditorProvider
        apiUrl={apiUrl}
        initialTemplate={parsed.value.template}
        data={parsed.value.data}
      >
        <div className="pdfua-template-island__stack">
          <div className="pdfua-template-island__pane pdfua-template-island__editor">
            <CodeEditor />
          </div>
          <div className="pdfua-template-island__pane pdfua-template-island__preview">
            <Preview />
          </div>
        </div>
      </TemplateEditorProvider>
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

function parseSafely(source: string) {
  try {
    return { ok: true as const, value: parseTemplateRequest(source) };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return { ok: false as const, message: `Unable to load template example: ${message}` };
  }
}
