export const MAX_CAPTURE_LENGTH = 5000;

/** Photos are shrunk to ≤1600 px in the browser first (src/lib/photo.ts), so real ones are ~150–300 KB. */
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
