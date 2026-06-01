import "@bambamboole/pdf-ua-template-builder/style.css";
import "./TemplateIslands.css";

import { useMemo, type ReactNode } from "react";
import { resolveDocsApiUrl } from "./apiUrl";
import { parseTemplateRequest } from "./templateRequest";
import { usePdfUaTemplateBuilder } from "./usePdfUaTemplateBuilder";

interface TemplateBuilderIslandProps {
  initialCode: string;
}

export default function TemplateBuilderIsland({ initialCode }: TemplateBuilderIslandProps) {
  const apiUrl = resolveDocsApiUrl(import.meta.env.PUBLIC_API_URL);
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
    return <IslandMessage>Loading template builder...</IslandMessage>;
  }

  if (moduleState.status === "error") {
    return <IslandMessage>Unable to load template builder: {moduleState.message}</IslandMessage>;
  }

  const { Builder, Preview, TemplateBuilderProvider } = moduleState.module;

  return (
    <div className="pdfua-template-island not-content">
      <TemplateBuilderProvider
        apiUrl={apiUrl}
        initialTemplate={parsed.value.template}
        initialData={parsed.value.data}
      >
        <div className="pdfua-template-island__stack">
          <div className="pdfua-template-island__pane pdfua-template-island__builder">
            <Builder />
          </div>
          <div className="pdfua-template-island__pane pdfua-template-island__preview">
            <Preview />
          </div>
        </div>
      </TemplateBuilderProvider>
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
