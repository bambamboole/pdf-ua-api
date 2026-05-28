import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

declare const process: { env: Record<string, string | undefined> };

export default defineConfig({
  plugins: [react()],
  base: "/template-builder/",
  build: {
    outDir: process.env.PDF_UA_TEMPLATE_BUILDER_OUT_DIR ?? "dist",
    emptyOutDir: true,
    rollupOptions: {
      output: {
        entryFileNames: "assets/app.js",
        chunkFileNames: "assets/[name].js",
        assetFileNames: (asset) =>
          asset.names?.some((name) => name.endsWith(".css"))
            ? "assets/style.css"
            : "assets/[name][extname]",
      },
    },
  },
});
