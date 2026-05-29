import {defineConfig} from "astro/config";
import starlight from "@astrojs/starlight";
import react from "@astrojs/react";
import starlightOpenAPI, {openAPISidebarGroups} from "starlight-openapi";

const base = process.env.BASE_PATH ?? "/";
const site = process.env.SITE_URL ?? "https://bambamboole.github.io";

export default defineConfig({
    site,
    base,
    srcDir: './docs',
    integrations: [
        starlight({
            title: "PDF UA API",
            social: [
                {icon: "github", label: "GitHub", href: "https://github.com/bambamboole/pdf-ua-api"},
            ],
            plugins: [
                starlightOpenAPI([
                    {base: "api", label: "API Reference", schema: "./docs/openapi/openapi.yaml"},
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
                {
                    label: "HTML",
                    items: [
                        {label: "HTML to PDF", link: "/html/html-to-pdf/"},
                        {label: "HTML to Image", link: "/html/html-to-image/"},
                    ],
                },
                {
                    label: "Templates",
                    items: [
                        {label: "Structure", link: "/templates/structure/"},
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
});
