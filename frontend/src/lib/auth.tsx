"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { api, ApiError, getToken, setToken } from "@/lib/api";

export interface User {
  id: string;
  email: string;
  displayName: string;
}

interface AuthResponse {
  token: string;
  user: User;
}

type Status = "loading" | "authenticated" | "anonymous";

interface AuthContextValue {
  user: User | null;
  status: Status;
  login: (email: string, password: string) => Promise<void>;
  signup: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<Status>("loading");

  useEffect(() => {
    if (!getToken()) {
      setStatus("anonymous");
      return;
    }
    api<User>("/api/auth/me")
      .then((u) => {
        setUser(u);
        setStatus("authenticated");
      })
      .catch((e) => {
        if (e instanceof ApiError && e.status === 401) setToken(null);
        setStatus("anonymous");
      });
  }, []);

  const accept = useCallback((res: AuthResponse) => {
    setToken(res.token);
    setUser(res.user);
    setStatus("authenticated");
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      accept(await api<AuthResponse>("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) }));
    },
    [accept],
  );

  const signup = useCallback(
    async (email: string, password: string, displayName: string) => {
      accept(
        await api<AuthResponse>("/api/auth/signup", {
          method: "POST",
          body: JSON.stringify({ email, password, displayName }),
        }),
      );
    },
    [accept],
  );

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    setStatus("anonymous");
    queryClient.clear();
  }, [queryClient]);

  const value = useMemo(() => ({ user, status, login, signup, logout }), [user, status, login, signup, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}
