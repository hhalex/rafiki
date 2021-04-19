const path = require("path");
const configBase = require("./webpack.config.base");
const CopyPlugin = require('copy-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
 
module.exports = {
  ...configBase,
  module: {
    rules: [
      ...configBase.module.rules,
      {
        test: /\.css$/i,
        use: [MiniCssExtractPlugin.loader, "css-loader"],
      }
    ]
  },
  plugins: [
    ...configBase.plugins,
    new CopyPlugin({
        patterns: [
            {from: "public/robots.txt", to: "robots.txt"},
            {from: "public/manifest.json", to: "manifest.json"}
        ]
    }),
    new MiniCssExtractPlugin({filename: "static/css/[name].[contenthash].css"})
  ],
  optimization: {
    splitChunks: {
      chunks: 'all',
    },
  }
}