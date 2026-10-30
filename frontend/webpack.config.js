/*
 * Copyright 2026 Allette Systems
 * SPDX-License-Identifier: Apache-2.0
 */
const path = require('path');
const fs = require('fs');

// Both bundles are named after the application version
const appVersion = fs.readFileSync('../version.txt', 'utf8').trim();

// Webpack Plugins
const { BundleAnalyzerPlugin } = require('webpack-bundle-analyzer');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CssMinimizerPlugin = require('css-minimizer-webpack-plugin');
const WebpackBar = require('webpackbar');

const setPath = (folderName) => path.join(__dirname, folderName);

module.exports = function (env, argv) {
  const dev = argv.mode === 'development';

  return {
    mode: dev ? 'development' : 'production',

    // The default development source maps use eval(), which the CSP blocks
    devtool: dev ? 'source-map' : false,

    // One entry point per Berlioz service group
    entry: {
      bzadmin: './src/js/bzadmin.js'
    },

    output: {
      path: dev ? path.resolve(__dirname) : setPath('../overlay/src/main/resources/META-INF/resources'),
      // Relative to the CSS file, so fonts and images resolve under any context path
      publicPath: 'auto',
      filename: `js/[name]-${appVersion}.min.js`,
      // Production builds replace the previous bundles, fonts and images (nothing else is generated there)
      clean: dev ? false : { keep: (asset) => !/^(js|css)[\\/]/.test(asset) }
    },

    // Dev server proxies to our Jetty for HTML and API
    devServer: {
      client: {
        logging: 'info',
        overlay: {
          warnings: false,
          errors: true
        }
      },
      compress: true,
      devMiddleware: {
        index: false, // disable to enable root proxying
        publicPath: '/',
        stats: 'minimal'
      },
      port: 9000,
      proxy: [
        {
          context: () => true,
          target: 'https://localhost:8444',
          secure: false
        }
      ],
      server: 'https'
    },

    module: {
      rules: [
        {
          test: /\.css$/,
          // Extracted in development too: the CSP of the admin pages has no 'unsafe-inline' style
          use: [MiniCssExtractPlugin.loader, 'css-loader']
        },
        {
          test: /\.(png|jpg|gif|svg|webp)$/,
          type: 'asset/resource',
          generator: {
            filename: 'css/bzadmin/images/[name].[hash:4][ext][query]'
          }
        },
        {
          test: /\.(eot|woff|woff2|ttf)$/,
          type: 'asset/resource',
          generator: {
            filename: 'css/bzadmin/fonts/[name].[hash:4][ext][query]'
          }
        }
      ]
    },

    // Minimised in production only; '...' keeps the default JS minimiser (Terser)
    optimization: {
      minimizer: [
        '...',
        new CssMinimizerPlugin({
          minimizerOptions: {
            preset: [
              'default',
              // Turned off it messes up a few colors
              { colormin: false }
            ]
          }
        })
      ]
    },

    plugins: [
      new WebpackBar(),
      new MiniCssExtractPlugin({ filename: `css/[name]-${appVersion}.min.css` }),
      // Bundle analysis, only with `npm run analyze`
      env.analyze &&
        new BundleAnalyzerPlugin({
          analyzerMode: 'static',
          generateStatsFile: true,
          openAnalyzer: false,
          reportFilename: setPath('analyzer/report.html'),
          statsFilename: setPath('analyzer/stats.json')
        })
    ]
  };
};
