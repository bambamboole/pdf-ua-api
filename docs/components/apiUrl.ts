export const DEFAULT_API_URL = "https://pdf-ua-api.c1.bambamboole.com";

export function resolveDocsApiUrl(configuredApiUrl: string | undefined): string {
  return configuredApiUrl || DEFAULT_API_URL;
}
