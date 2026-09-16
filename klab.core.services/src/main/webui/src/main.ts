import { createApp } from "vue";
import { Notify, Quasar } from "quasar";

import "@quasar/extras/material-icons/material-icons.css";
import "quasar/dist/quasar.css";

import App from "./App.vue";
import "./styles.css";

createApp(App)
  .use(Quasar, {
    plugins: { Notify },
    config: {
      brand: {
        primary: "#18b795",
        secondary: "#9ab558",
        dark: "#092f2a",
      },
    },
  })
  .mount("#app");