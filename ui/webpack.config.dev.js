const path = require("path");
const configBase = require("./webpack.config.base");
 
module.exports = {
  ...configBase,
  entry: [
    "webpack-dev-server/client?http://localhost:9000/",
    ...configBase.entry
  ],
  module: {
    rules: [
      ...configBase.module.rules,
      {
        test: /\.css$/i,
        use: ["style-loader", "css-loader"],
      }
    ]
  },
  plugins: [
      ...configBase.plugins
  ],
  mode: "development",
  devtool: "inline-source-map",
  devServer: {
    port: 9000,
    hot:true,
    historyApiFallback: true,
    headers: {
        "Access-Control-Allow-Origin": "*"
    },
    proxy: {
      '/api': 'http://localhost:8080',
      '/login': 'http://localhost:8080'
    }
  }
}