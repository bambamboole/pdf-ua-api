import type { Template, TemplateData } from "@bambamboole/pdf-ua-template-builder";

export interface ParsedTemplateRequest {
  template: Template;
  data: TemplateData;
}

export function parseTemplateRequest(source: string): ParsedTemplateRequest {
  const parsed = JSON.parse(source) as unknown;

  if (!isRecord(parsed)) {
    throw new Error("Expected a JSON object.");
  }

  const template = "template" in parsed ? parsed.template : parsed;
  const data = "data" in parsed && isRecord(parsed.data) ? parsed.data : {};

  if (!isRecord(template)) {
    throw new Error("Expected a template object.");
  }

  return {
    template: template as Template,
    data: data as TemplateData,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
