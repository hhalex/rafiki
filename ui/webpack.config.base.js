const path = require("path");
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = {
  entry: ["./src/index.tsx"],
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".json"]
  },
  output: {
    path: path.join(__dirname, "/build"),
    publicPath: "/",
    filename: "static/js/[name].[contenthash].js"
  },
  module: {
    rules: [
      { 
        test: /\.tsx?$/, 
        loader: "awesome-typescript-loader"
      }
    ]
  },
  plugins: [
    new HtmlWebpackPlugin({
        template: "./public/index.html",
        favicon: "./public/favicon.ico",
        title: "Rafiki platform",
        meta: {
        charset: { charset: 'utf-8' },
        viewport: 'width=device-width, initial-scale=1',
        description: "Rafiki platform",
        "theme-color": "#000000"
        },
        minify: false
    })
    ] 
}