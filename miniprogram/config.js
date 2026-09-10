/**
 * 运行环境配置
 *
 * - dev：本机联调用（需在开发者工具勾选「不校验合法域名」）
 * - prod：上线前改成备案域名（https）
 */
const ENV = 'dev'

const CONFIG = {
  dev: {
    baseUrl: 'http://localhost:8080',
  },
  prod: {
    baseUrl: 'https://api.example.com',
  },
}

module.exports = Object.assign({ env: ENV }, CONFIG[ENV])
