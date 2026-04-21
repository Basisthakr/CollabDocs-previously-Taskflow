// All runtime URLs are read from Vite environment variables so the same
// build artifact works in local dev, staging, and production without code changes.
//
// Set them in a .env file (see .env.example at the project root) or as
// environment variables before running `vite build`.
//
//   VITE_API_URL  — HTTP base URL of the Spring Boot backend
//   VITE_WS_URL   — WebSocket URL for the STOMP endpoint

export const BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
export const WS_URL   = import.meta.env.VITE_WS_URL  ?? "ws://localhost:8080/docs/ws";
