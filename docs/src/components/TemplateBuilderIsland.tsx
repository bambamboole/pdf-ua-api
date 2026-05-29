import { TemplateBuilder, createInvoiceExample } from "@bambamboole/pdf-ua-template-builder";
import "@bambamboole/pdf-ua-template-builder/style.css";

const DEFAULT_API_URL = "http://localhost:8080";

export default function TemplateBuilderIsland() {
  const apiUrl = import.meta.env.PUBLIC_API_URL ?? DEFAULT_API_URL;
  const example = createInvoiceExample();
  return (
    <TemplateBuilder
      apiUrl={apiUrl}
      initialTemplate={example.template}
      initialData={example.data}
    />
  );
}
