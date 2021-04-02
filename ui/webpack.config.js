const path = require("path");
const HtmlWebpackPlugin = require("html-webpack-plugin");
const CopyPlugin = require('copy-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
 
module.exports = {
  entry: "./src/index.tsx",
  devtool: "source-map",
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".json"]
  },
  output: {
    path: path.join(__dirname, "/build"),
    filename: "static/js/[name].[contenthash].js"
  },
  module: {
    rules: [
      { 
        test: /\.tsx?$/, 
        loader: "awesome-typescript-loader"
      },
      {
        test: /\.css$/i,
        use: [MiniCssExtractPlugin.loader, "css-loader"],
      },
    ]
  },
  plugins: [
    new HtmlWebpackPlugin({
      template: "./public/index.html",
      publicPath: "",
      favicon: "./public/favicon.ico",
      title: "Rafiki platform",
      meta: {
        charset: { charset: 'utf-8' },
        viewport: 'width=device-width, initial-scale=1',
        description: "Rafiki platform",
        "theme-color": "#000000"
      },
      minify: false
    }),
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
  },
  devServer: {
    proxy: {
      '*': 'http://localhost:8080',
    },
  },
}