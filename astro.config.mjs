import {defineConfig} from "astro/config";
import starlight from "@astrojs/starlight";
import react from "@astrojs/react";
import starlightOpenAPI, {openAPISidebarGroups} from "starlight-openapi";
import tailwindcss from "@tailwindcss/vite";
import remarkGfm from "remark-gfm";

// Served at the root of the custom domain, so the base is "/" unless an explicit
// BASE_PATH is provided. `||` guards against an empty string from CI's configure-pages.
const base = process.env.BASE_PATH || "/";
const site = process.env.SITE_URL || "https://pdf-ua-api.bambamboole.com";
const viteCacheSuffix = process.argv.includes("build") ? "build" : "dev";

export default defineConfig({
    site,
    base,
    srcDir: './docs',
    markdown: {
        remarkPlugins: [remarkGfm],
    },
    integrations: [
        starlight({
            title: "PDF UA API",
            social: [
                {icon: "github", label: "GitHub", href: "https://github.com/bambamboole/pdf-ua-api"},
            ],
            customCss: ["./docs/styles/global.css"],
            plugins: [
                starlightOpenAPI([
                    {base: "api", label: "API Reference", schema: "./docs/openapi/openapi.json"},
                ]),
            ],
            sidebar: [
                {
                    label: "Getting Started",
                    items: [
                        {label: "Introduction", link: "/"},
                        {label: "Quick Start", link: "/getting-started/quick-start/"},
                    ],
                },
                {label: "Authentication", link: "/authentication/"},
                {label: "Rate Limiting", link: "/rate-limiting/"},
                {label: "S3 PDF Upload", link: "/s3-upload/"},
                {
                    label: "HTML",
                    items: [
                        {label: "HTML to PDF", link: "/html/html-to-pdf/"},
                        {label: "URL to PDF", link: "/html/url-to-pdf/"},
                        {label: "HTML to Image", link: "/html/html-to-image/"},
                    ],
                },
                {
                    label: "Templates",
                    items: [
                        {label: "Structure", link: "/templates/structure/"},
                        {label: "Builder", link: "/templates/builder/"},
                        {label: "Background", link: "/templates/background/"},
                        {label: "External Fonts", link: "/templates/external-fonts/"},
                        {label: "Invoice", link: "/templates/invoice/"},
                    ],
                },
                ...openAPISidebarGroups,
            ],
        }),
        react(),
    ],
    vite: {
        cacheDir: `node_modules/.vite-${viteCacheSuffix}`,
        plugins: [tailwindcss()],
    },
});
