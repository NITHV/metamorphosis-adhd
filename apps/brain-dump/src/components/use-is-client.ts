import { useSyncExternalStore } from "react";

const noopSubscribe = () => () => {};

/**
 * true in the browser, false during server render. Use it for anything that depends on
 * the viewer's clock or timezone, so server and browser HTML match.
 */
export function useIsClient() {
  return useSyncExternalStore(
    noopSubscribe,
    () => true,
    () => false,
  );
}
