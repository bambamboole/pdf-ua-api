import { useEffect, useState } from "react";

type BuilderModule = typeof import("@bambamboole/pdf-ua-template-builder");

type ModuleState =
  | { status: "loading" }
  | { status: "ready"; module: BuilderModule }
  | { status: "error"; message: string };

export function usePdfUaTemplateBuilder(): ModuleState {
  const [state, setState] = useState<ModuleState>({ status: "loading" });

  useEffect(() => {
    let mounted = true;

    import("@bambamboole/pdf-ua-template-builder")
      .then((module) => {
        if (mounted) {
          setState({ status: "ready", module });
        }
      })
      .catch((error: unknown) => {
        if (mounted) {
          const message = error instanceof Error ? error.message : String(error);
          setState({ status: "error", message });
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  return state;
}
