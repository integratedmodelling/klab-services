// Language definitions, Monarch tokenizer, theme and formatter from KlabMonacoEditor
// (6e30cc2aaff2520c6e528e4e3bb64449618cfcdb, AGPL-3.0).
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import { KlabFormatting } from "./formatting";


type KlabLanguageName = "kim" | "kactor" | "worldview" | "observation";

interface KlabLanguageSpec {
    name: KlabLanguageName;
    id: string;
    aliases: string[];
    extensions: string[];
}

const KLAB_LANGUAGES: KlabLanguageSpec[] = [
    {name: "kim", id: "org.integratedmodelling.languages.Kim", aliases: ["k.IM", "kim"], extensions: [".kim"]},
    {
        name: "kactor",
        id: "org.integratedmodelling.languages.KActors",
        aliases: ["k.Actors", "kactor", "kactors"],
        extensions: [".kactor", ".kactors"]
    },
    {
        name: "worldview",
        id: "org.integratedmodelling.languages.Worldview",
        aliases: ["k.Worldview", "worldview", "kwv"],
        extensions: [".kwv"]
    },
    {
        name: "observation",
        id: "org.integratedmodelling.languages.Observation",
        aliases: ["k.Observation", "observation", "obs"],
        extensions: [".obs", ".observation"]
    }
];

// Word-like parser literals from Observable.xtext and the four document grammars.
// Available when the editor opens, including when LSP metadata is late or unavailable.
const OBSERVABLE_KEYWORDS = (
    "about adjacent all and any as averaged by caused causing change changed contained containing count distance " +
    "down during each exists false for from in is level linking magnitude minus monetary named no not observed " +
    "occurrence of optional or over percentage plus presence probability proportion rate ratio required summed " +
    "times to total true type uncertainty value when where whose with within without"
).split(" ");
const BUILT_IN_KEYWORDS: Record<KlabLanguageName, string[]> = {
    kim: (
        "AD BC CE according boolean classified column default define discretized disjoint do event events exclusive " +
        "if inclusive initialization integrate into learn lookup match metadata model namespace number observing on " +
        "otherwise private project public relationships row scenario set subjects termination text transition unknown " +
        "unless using version void worldview"
    ).split(" ").concat(OBSERVABLE_KEYWORDS),
    kactor: (
        "action app assert behavior behaviour break component def desktop do else empty exception fail fire if " +
        "inherits library mobile ok properties public return script set static switch task testcase then trait " +
        "unknown user using version web while worldview yield"
    ).split(" ").concat(OBSERVABLE_KEYWORDS),
    worldview: (
        "abstract acceleration affected affects agent amount angle applies area attribute authority bond charge " +
        "children class classifies confers configuration contains core created creates decreases deliberative deniable " +
        "describes directed discretizes disjoint domain duration electric-potential emerges energy entropy equals event " +
        "extent functional has identity implies increases individual inherits interactive into length links marks mass " +
        "metadata money ontology ordering pressure priority process quality quantity reactive realm relationship " +
        "requires rescaling resistance resistivity role root sealed source structural subjective target temperature " +
        "thing using velocity version viscosity volume weight"
    ).split(" ").concat(OBSERVABLE_KEYWORDS),
    observation: (
        "ACKNOWLEDGEMENT CATEGORIZATION CHARACTERIZATION CLASSIFICATION CONNECTION DETECTION INSTANTIATION MEASURE " +
        "QUANTIFICATION SIMULATION TRANSFORMATION VALUATION VERIFICATION VOID absent abstract activity adaptive agent " +
        "aggregate apply attribute bind canonical capability capture checksum clauses collective comparison component " +
        "condition configuration contains continuation coverage dataflow define either ensure event every exact exactly " +
        "geometry head identification identity if inherent inputs into kind let links literal logical logical-intersection " +
        "logical-union matcher media merge metadata mode mosaic namespace negated node observable observation observe on " +
        "one_of operand operands operator otherwise output parameters pattern payload predicate predicates present process " +
        "quality realm ref reference relationship replay request requires resolve resource rest role roles same select " +
        "semantic semantic_operator single strategies strategy subject transform unordered using value_operators version " +
        "worldview yield yielding"
    ).split(" ").concat(OBSERVABLE_KEYWORDS)
};

const COLOR_RGB: { [name: string]: number[] } = {
    DOMAIN: [105, 255, 254],
    CONFIGURATION: [0, 100, 100],
    EVENT: [153, 153, 0],
    EXTENT: [0, 153, 153],
    PROCESS: [204, 0, 0],
    QUALITY: [0, 204, 0],
    RELATIONSHIP: [210, 170, 0],
    TRAIT: [0, 102, 204],
    ROLE: [0, 86, 163],
    SUBJECT: [153, 76, 0],
    LIVE_URN: [0, 102, 0],
    INACTIVE_URN: [255, 215, 0],
    ERROR: [255, 0, 0],
    UNKNOWN: [128, 128, 128],
    INACTIVE: [160, 160, 160],
    VERSION: [0, 153, 153],
    KEYWORD: [85, 6, 22],
    VALUE_OPERATOR: [0, 0, 0],
    UNARY_OPERATOR: [0, 0, 0],
    BINARY_OPERATOR: [0, 0, 0],
    SEMANTIC_MODIFIER: [0, 0, 0]
};

// Keep the light palette above stable. These values are only used by the dark theme;
// each color preserves the hue of its light-theme counterpart while increasing contrast.
const DARK_COLOR_RGB: { [name: string]: number[] } = {
    DOMAIN: [103, 232, 231],
    CONFIGURATION: [92, 200, 200],
    EVENT: [230, 214, 106],
    EXTENT: [104, 217, 217],
    PROCESS: [255, 133, 133],
    QUALITY: [120, 224, 143],
    RELATIONSHIP: [241, 207, 112],
    TRAIT: [114, 174, 245],
    ROLE: [115, 169, 237],
    SUBJECT: [224, 160, 110],
    LIVE_URN: [105, 201, 138],
    INACTIVE_URN: [255, 229, 138],
    ERROR: [255, 123, 123],
    UNKNOWN: [196, 196, 196],
    INACTIVE: [192, 192, 192],
    VERSION: [112, 217, 217],
    KEYWORD: [255, 157, 181],
    VALUE_OPERATOR: [212, 212, 212],
    UNARY_OPERATOR: [212, 212, 212],
    BINARY_OPERATOR: [212, 212, 212],
    SEMANTIC_MODIFIER: [212, 212, 212]
};

function rgb(name: string): string {
    const value = COLOR_RGB[(name || "").toUpperCase()] || COLOR_RGB.UNKNOWN;
    return "#" + value.map((part) => {
        const hex = Math.max(0, Math.min(255, part)).toString(16);
        return hex.length === 1 ? "0" + hex : hex;
    }).join("");
}

function darkRgb(name: string): string {
    const value = DARK_COLOR_RGB[(name || "").toUpperCase()] || DARK_COLOR_RGB.UNKNOWN;
    return "#" + value.map((part) => {
        const hex = Math.max(0, Math.min(255, part)).toString(16);
        return hex.length === 1 ? "0" + hex : hex;
    }).join("");
}

function tokenName(name: string): string {
    return "klab-" + (name || "unknown").toLowerCase().replace(/[^a-z0-9_-]/g, "-");
}

function normalizeLanguage(language?: string): KlabLanguageSpec | null {
    const value = (language || "").toLowerCase();
    if (!value) return null;
    for (const spec of KLAB_LANGUAGES) {
        if (value === spec.name || value === spec.id.toLowerCase()) return spec;
        if (spec.aliases.some((alias) => value === alias.toLowerCase())) return spec;
        if (spec.extensions.some((extension) => value.endsWith(extension.toLowerCase()))) return spec;
    }
    return null;
}

export function installKlabTheme() {
    const rules: monaco.editor.ITokenThemeRule[] = [
        {token: "klab.keyword", foreground: rgb("KEYWORD").substring(1), fontStyle: "bold"},
        // Metadata keys are intentionally muted and must not inherit keyword styling.
        {token: "klab.metadata", foreground: "808080"},
        {token: "klab.comment", foreground: "4ea64e", fontStyle: "italic"},
        {token: "klab.doccomment", foreground: "0000a0"},
        {token: "klab.operator", foreground: rgb("VALUE_OPERATOR").substring(1)},
        {token: "klab.number", foreground: "098658"},
        {token: "klab.string", foreground: "014732"},
        {token: "klab.delimiter", foreground: "000000"}
    ];

    for (const name in COLOR_RGB) {
        const token = "klab." + tokenName(name);
        rules.push({token, foreground: rgb(name).substring(1)});
    }

    // Monarch appends tokenPostfix to emitted tokens. Keep an exact keyword rule
    // after the generated category rules so keyword styling remains unambiguous.
    rules.push({token: "klab.keyword.klab", foreground: rgb("KEYWORD").substring(1), fontStyle: "bold"});

    const darkRules = rules.map((rule) => {
        switch (rule.token) {
            case "klab.keyword":
                return {...rule, foreground: darkRgb("KEYWORD").substring(1)};
            case "klab.metadata":
                return {...rule, foreground: "b0b0b0"};
            case "klab.comment":
                return {...rule, foreground: "7fd18b"};
            case "klab.doccomment":
                return {...rule, foreground: "8fb4ff"};
            case "klab.operator":
            case "klab.delimiter":
                return {...rule, foreground: "d4d4d4"};
            case "klab.number":
                return {...rule, foreground: "8bd5ff"};
            case "klab.string":
                return {...rule, foreground: "a8e6a3"};
            default:
                if (rule.token.indexOf("klab.klab-") === 0) {
                    const colorName = rule.token.substring("klab.klab-".length).toUpperCase();
                    return {...rule, foreground: darkRgb(colorName).substring(1)};
                }
                return rule;
        }
    });
    darkRules.push({token: "klab.keyword.klab", foreground: darkRgb("KEYWORD").substring(1), fontStyle: "bold"});

    try {
        monaco.editor.defineTheme("klab-vs", {
            base: "vs",
            inherit: true,
            rules,
            colors: {}
        });
        monaco.editor.defineTheme("klab-vs-dark", {
            base: "vs-dark",
            inherit: true,
            rules: darkRules,
            colors: {}
        });
    } catch (e) {
        console.warn("Failed to define k.LAB themes", e);
    }
}

export function klabThemeName(theme: string): string {
    return (theme || "").toLowerCase().indexOf("dark") >= 0 ? "klab-vs-dark" : "klab-vs";
}

function createKlabMonarch(keywords: string[]): monaco.languages.IMonarchLanguage {
    const compoundKeywords = keywords.filter(keyword => /^\w+(?:-\w+)+$/.test(keyword));
    const compoundRules: monaco.languages.IMonarchLanguageRule[] = compoundKeywords.length ? [
        [new RegExp("(?:"
            + compoundKeywords.join("|") + ")(?![\\w$-])"), "klab.keyword"]
    ] : [];
    return {
        defaultToken: "",
        tokenPostfix: ".klab",
        keywords: keywords || [],
        operators: [
            "=", ">", "<", "!", "~", "?", ":", "==", "<=", ">=", "!=", "&&", "||", "++", "--",
            "+", "-", "*", "/", "&", "|", "^", "%", "<<", ">>", ">>>", "+=", "-=", "*=", "/=",
            "&=", "|=", "^=", "%=", "<<=", ">>=", ">>>="
        ],
        symbols: /[=><!~?:&|+\-*\/\^%]+/,
        escapes: /\\(?:[btnfr"'\\]|u[0-9A-Fa-f]{4})/,
        tokenizer: {
            root: [
                [/\/\*\*\*/, "klab.doccomment", "@doccomment"],
                [/\/\*/, "klab.comment", "@comment"],
                [/\/\/.*$/, "klab.comment"],
                [/"/, "klab.string", "@string_double"],
                [/'/, "klab.string", "@string_single"],
                [/\d*\.\d+([eE][\-+]?\d+)?[fFdD]?/, "klab.number.float"],
                [/0[xX][0-9a-fA-F]+[lL]?/, "klab.number.hex"],
                [/\d+[lL]?/, "klab.number"],
                [/[a-z]+(?:\.[a-z]+)*:[A-Z][A-Za-z0-9]*/, "klab." + tokenName("UNKNOWN")],
                [/[a-z_][a-z0-9_]*(?:\.[a-z0-9_]+)*:[a-z_][a-z0-9_]*(?:\.[a-z0-9_]+)*:[a-z_][a-z0-9_]*(?:\.[a-z0-9_]+)*:[a-z_][a-z0-9_]*(?:\.[a-z0-9_]+)*(?:#[A-Za-z_][A-Za-z0-9_]*(?:=[^,\s\]\)\};]+)?(?:,[A-Za-z_][A-Za-z0-9_]*(?:=[^,\s\]\)\};]+)?)*)?/, "klab." + tokenName("UNKNOWN")],
                // Consume the complete metadata key before identifier/keyword rules run.
                [/[:+!][a-z][a-z0-9_]*/, "klab.metadata"],
                // Match paths as a whole so keyword segments remain identifiers.
                [/[a-zA-Z_$][\w$]*(?:\.[a-zA-Z_$][\w$]*)+/, "identifier"],
                ...compoundRules,
                [/[a-zA-Z_$][\w$]*/, {cases: {"@keywords": "klab.keyword", "@default": "identifier"}}],
                [/@symbols/, {cases: {"@operators": "klab.operator", "@default": ""}}],
                [/\[/, "@brackets", "@groovy"],
                [/===/, "klab.delimiter"],
                [/\{\{/, "klab.delimiter"],
                [/\}\}/, "klab.delimiter"],
                [/[{}()]/, "@brackets"],
                [/[;,.]/, "klab.delimiter"]
            ],
            comment: [
                [/[^\/*]+/, "klab.comment"],
                [/\*\//, "klab.comment", "@pop"],
                [/[\/*]/, "klab.comment"]
            ],
            doccomment: [
                [/[^\/*]+/, "klab.doccomment"],
                [/\*\//, "klab.doccomment", "@pop"],
                [/[\/*]/, "klab.doccomment"]
            ],
            string_double: [
                [/[^\\"]+/, "klab.string"],
                [/@escapes/, "klab.string.escape"],
                [/\\./, "klab.string.escape.invalid"],
                [/"/, "klab.string", "@pop"]
            ],
            string_single: [
                [/[^\\']+/, "klab.string"],
                [/@escapes/, "klab.string.escape"],
                [/\\./, "klab.string.escape.invalid"],
                [/'/, "klab.string", "@pop"]
            ],
            groovy: [
                [/[^\]]+/, ""],
                [/\]/, "@brackets", "@pop"]
            ]
        }
    };
}

const registered = new Set<string>();

export function registerLanguage(language: string): string {
  const spec = normalizeLanguage(language);
  if (!spec) return "plaintext";
  if (registered.has(spec.id)) return spec.id;
  monaco.languages.register({ id: spec.id, aliases: spec.aliases, extensions: spec.extensions });
  monaco.languages.registerDocumentFormattingEditProvider(spec.id, {
    displayName: "k.LAB",
    provideDocumentFormattingEdits(model, _options, token) {
      if (token.isCancellationRequested) return [];
      return KlabFormatting.lineEdits(model.getLinesContent(), spec.name).map(edit => ({
        range: new monaco.Range(edit.line, 1, edit.line, model.getLineMaxColumn(edit.line)),
        text: edit.text.join(model.getEOL()),
      }));
    },
  });
  monaco.languages.setMonarchTokensProvider(spec.id, createKlabMonarch(BUILT_IN_KEYWORDS[spec.name]));

    monaco.languages.setLanguageConfiguration(spec.id, {
        comments: {lineComment: "//", blockComment: ["/*", "*/"]},
        brackets: [["{{", "}}"], ["{", "}"], ["[", "]"], ["(", ")"]],
        autoClosingPairs: [
            {open: "===", close: "==="},
            {open: "{{", close: "}}"},
            {open: "{", close: "}"},
            {open: "[", close: "]"},
            {open: "(", close: ")"},
            {open: "\"", close: "\""},
            {open: "'", close: "'"}
        ],
        surroundingPairs: [
            {open: "===", close: "==="},
            {open: "{{", close: "}}"},
            {open: "{", close: "}"},
            {open: "[", close: "]"},
            {open: "(", close: ")"},
            {open: "\"", close: "\""},
            {open: "'", close: "'"}
        ]
    });

  registered.add(spec.id);
  return spec.id;
}
