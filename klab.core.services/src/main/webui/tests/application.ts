import {createApp, defineComponent, h, reactive} from "vue";
import {Quasar} from "quasar";
import ActorApplication from "../src/components/ActorApplication.vue";
import "quasar/dist/quasar.css";

const auth = reactive({ready: true, enabled: true, authenticated: false, username: "alice", error: ""});
const request = async (path: string, init?: RequestInit) => {
  const response = await fetch(new URL(path, document.baseURI), init);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return response.status === 204 ? undefined : response.json();
};
const context = {auth, config: {title: "Runtime"}, api: {get: request, request}} as any;
const app = createApp(defineComponent({setup() { return () => h("div", [
  h("button", {id: "fixture-login", onClick: () => { auth.authenticated = true; }}, "Authenticate fixture user"),
  h("button", {id: "fixture-logout", onClick: () => { auth.authenticated = false; }}, "Logout fixture user"),
  h(ActorApplication, {urn: "examples.workflow", context}),
]); }}));
app.use(Quasar); app.mount("#app");
