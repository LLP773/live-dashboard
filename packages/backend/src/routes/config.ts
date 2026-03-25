const displayName = process.env.DISPLAY_NAME || "Monika";

export function handleConfig(): Response {
  return Response.json({
    displayName,
    siteTitle: process.env.SITE_TITLE || `${displayName} Now`,
    siteDescription: process.env.SITE_DESC || `What is ${displayName} doing right now?`,
    siteFavicon: process.env.SITE_FAVICON || "/favicon.ico",
  });
}
