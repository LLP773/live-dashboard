const displayName = process.env.DISPLAY_NAME || "Monika";

export function handleConfig(): Response {
  return Response.json({ displayName });
}
