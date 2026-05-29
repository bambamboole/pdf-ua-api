import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";
import react from "@astrojs/react";

const base = process.env.BASE_PATH ?? "/";
const site = process.env.SITE_URL ?? "https://bambamboole.github.io";

export default defineConfig({
  site,
  base,
  integrations: [
    starlight({
      title: "PDF UA API",
      social: [
        { icon: "github", label: "GitHub", href: "https://github.com/bambamboole/pdf-ua-api" },
      ],
      sidebar: [
        {
          label: "Getting Started",
          items: [
            { label: "Introduction", link: "/" },
            { label: "Quick Start", link: "/getting-started/quick-start/" },
          ],
        },
        {
          label: "Guides",
          autogenerate: { directory: "guides" },
        },
      ],
    }),
    react(),
  ],
});
