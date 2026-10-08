import type { MetadataRoute } from "next";

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
  };
}
