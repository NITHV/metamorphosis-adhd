"use client";

// Photo dumps are shrunk on the phone before they're stored or uploaded (design doc §2, "Photo dumps"):
// - longest side at most 1600 px, WebP (JPEG where the browser can't make WebP): ~150–300 KB instead of MBs
// - re-drawing the pixels onto a canvas drops everything hidden in the original file, including the
//   GPS location phones embed. Only the pixels survive.

import { MAX_PHOTO_BYTES } from "./limits";

export const MAX_PHOTO_SIDE = 1600;

export class PhotoError extends Error {}

/** Shrinks a photo from the camera, gallery or share sheet. Throws PhotoError if it can't be read. */
export async function shrinkPhoto(file: Blob): Promise<Blob> {
  let bitmap: ImageBitmap;
  try {
    // "from-image" applies the phone's rotation flag, so the photo isn't sideways after the
    // hidden data (which held that flag) is dropped.
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    throw new PhotoError("Couldn't read that photo. Try a JPEG or PNG.");
  }

  const scale = Math.min(1, MAX_PHOTO_SIDE / Math.max(bitmap.width, bitmap.height));
  const width = Math.max(1, Math.round(bitmap.width * scale));
  const height = Math.max(1, Math.round(bitmap.height * scale));
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new PhotoError("Couldn't process that photo.");
  ctx.imageSmoothingQuality = "high";
  ctx.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();

  // Browsers that can't encode WebP quietly return a PNG instead; fall back to JPEG then.
  let blob = await toBlob(canvas, "image/webp", 0.8);
  if (!blob || blob.type !== "image/webp") blob = await toBlob(canvas, "image/jpeg", 0.85);
  if (!blob) throw new PhotoError("Couldn't process that photo.");
  if (blob.size > MAX_PHOTO_BYTES) throw new PhotoError("That photo is too large.");
  return blob;
}

function toBlob(canvas: HTMLCanvasElement, type: string, quality: number): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}

export function photoExtension(blob: Blob): "webp" | "jpg" {
  return blob.type === "image/webp" ? "webp" : "jpg";
}
