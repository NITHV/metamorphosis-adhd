ALTER TYPE "public"."capture_source" ADD VALUE 'photo';--> statement-breakpoint
ALTER TABLE "captures" ADD COLUMN "photo_url" text;