import { createApp } from "vue";
import { Dark, Notify, Quasar } from "quasar";

import "@quasar/extras/material-icons/material-icons.css";
import "quasar/dist/quasar.css";

import App from "./App.vue";
import "./styles/theme.css";
import "./styles.css";
import { initializeTheme } from "./services/theme";

const app = createApp(App)
  .use(Quasar, {
    plugins: { Notify, Dark },
  });
initializeTheme();
app.mount("#app");
