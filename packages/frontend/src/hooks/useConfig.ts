"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { fetchConfig } from "@/lib/api";
import type { SiteConfig } from "@/lib/api";

const ConfigContext = createContext<SiteConfig>({ displayName: "Monika" });

export function useConfig() {
  return useContext(ConfigContext);
}

export { ConfigContext };

export function useConfigLoader(): SiteConfig {
  const [config, setConfig] = useState<SiteConfig>({ displayName: "Monika" });

  useEffect(() => {
    const controller = new AbortController();
    fetchConfig(controller.signal)
      .then((c) => {
        if (!controller.signal.aborted) setConfig(c);
      })
      .catch(() => {});
    return () => controller.abort();
  }, []);

  return config;
}
