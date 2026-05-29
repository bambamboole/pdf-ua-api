import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { TemplateBuilder, createInvoiceExample } from "@bambamboole/pdf-ua-template-builder";
import "@bambamboole/pdf-ua-template-builder/style.css";

const root = document.getElementById("template-builder-root");

if (!root) {
  throw new Error("Template builder root element was not found.");
}

const example = createInvoiceExample();

createRoot(root).render(
  <StrictMode>
    <TemplateBuilder apiUrl="" initialTemplate={example.template} initialData={example.data} />
  </StrictMode>,
);
