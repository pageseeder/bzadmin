const js = require("@eslint/js");
const globals = require("globals");
const prettier = require("eslint-config-prettier");

module.exports = [
  // Ignore patterns (replaces .eslintignore)
  {
    ignores: ["node_modules/**"]
  },

  // Base JS recommended rules
  js.configs.recommended,

  // Project config
  {
    files: ["src/**/*.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: {
        ...globals.browser
      }
    },
    rules: {
      // Allow ++ in the final expression of a for loop only
      "no-plusplus": ["error", { "allowForLoopAfterthoughts": true }]
    }
  },

  // Unit tests run in Node (npm test)
  {
    files: ["src/**/*.test.js"],
    languageOptions: {
      globals: {
        ...globals.node
      }
    }
  },

  // Turn off rules that conflict with Prettier (must be last)
  prettier
];
