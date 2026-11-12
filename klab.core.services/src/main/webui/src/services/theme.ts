import { ref } from "vue";
import { Dark } from "quasar";

type Theme = "light" | "dark";
const storageKey = "klab-dashboard-theme";
function savedTheme(): Theme {
  try { return localStorage.getItem(storageKey) === "dark" ? "dark" : "light"; }
  catch { return "light"; }
}
export const theme = ref<Theme>(savedTheme());
function applyTheme() {
  document.documentElement.dataset.theme = theme.value;
  Dark.set(theme.value === "dark");
  const colour = getComputedStyle(document.documentElement).getPropertyValue("--klab-background").trim();
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", colour);
}
export function initializeTheme() {
  applyTheme();
  window.addEventListener("storage", event => {
    if (event.key === storageKey || event.key === null) { theme.value = savedTheme(); applyTheme(); }
  });
}
export function toggleTheme() {
  theme.value = theme.value === "light" ? "dark" : "light";
  try { localStorage.setItem(storageKey, theme.value); } catch { /* Session-only when storage is unavailable. */ }
  applyTheme();
}
