import type { MetadataRoute } from "next";

const shortcut = (name: string, short_name: string, url: string, icon: string) => ({
  name,
  short_name,
  url,
  icons: [{ src: `/icons/shortcut-${icon}.png`, sizes: "96x96", type: "image/png" }],
});

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Brain Dump",
    short_name: "Brain Dump",
    description: "Catch every thought. Sort it later.",
    start_url: "/",
    display: "standalone",
    background_color: "#ece5d6",
    theme_color: "#2f9e66",
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
      { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
    // Long-press the app icon on Android; each can also be dragged onto the home screen.
    shortcuts: [
      shortcut("Type a dump", "Type", "/?type=1", "type"),
      shortcut("Voice dump", "Voice", "/?voice=1", "voice"),
      shortcut("Photo dump", "Photo", "/?photo=1", "photo"),
      shortcut("Pause what I'm doing", "Pause", "/?pause=1", "pause"),
    ],
    // "Share → Brain Dump" from other apps. Handled by the service worker (public/sw.js).
    share_target: {
      action: "/share-target",
      method: "POST",
      enctype: "multipart/form-data",
      params: {
        title: "title",
        text: "text",
        url: "url",
        files: [{ name: "photos", accept: ["image/*"] }],
      },
    },
  };
}
