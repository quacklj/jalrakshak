import { subscribe } from "@/lib/store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Server-sent events: one `reading` event per sample the ESP32 posts. */
export async function GET(req: Request) {
  const encoder = new TextEncoder();

  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      const send = (event: string, data: unknown) => {
        try {
          controller.enqueue(encoder.encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`));
        } catch {
          /* client already gone */
        }
      };

      send("hello", { now: Date.now() });
      const unsubscribe = subscribe((reading) => send("reading", reading));

      // Keeps proxies from closing an idle connection.
      const beat = setInterval(() => send("ping", { now: Date.now() }), 20_000);

      const close = () => {
        clearInterval(beat);
        unsubscribe();
        try {
          controller.close();
        } catch {
          /* already closed */
        }
      };
      req.signal.addEventListener("abort", close);
    },
  });

  return new Response(stream, {
    headers: {
      "content-type": "text/event-stream; charset=utf-8",
      "cache-control": "no-cache, no-transform",
      connection: "keep-alive",
      "x-accel-buffering": "no",
    },
  });
}
