module.exports = function (api) {
  const isTest = api.env("test");
  api.cache.using(() => isTest);

  return {
    presets: ["babel-preset-expo"],
    plugins: [
      [
        "module-resolver",
        {
          root: ["./"],
          alias: { "@": "./src" },
        },
      ],
      // Tamagui's static-extraction babel plugin parses tamagui.config.ts with its own
      // esbuild-based tool, which chokes under Jest's transform pipeline — skip it in tests,
      // components still render correctly via Tamagui's runtime (non-extracted) path.
      !isTest && [
        "@tamagui/babel-plugin",
        {
          components: ["tamagui"],
          config: "./tamagui.config.ts",
          logTimings: false,
        },
      ],
      "react-native-reanimated/plugin",
    ].filter(Boolean),
  };
};
