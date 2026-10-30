import { createApp } from "vue";
import { Quasar } from "quasar";
import CredentialsPanel from "../src/components/CredentialsPanel.vue";
import "quasar/dist/quasar.css";
createApp(CredentialsPanel).use(Quasar).mount("#app");
