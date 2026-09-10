/** 会员接口 */
const request = require('../utils/request')

/** 会员资料（脱敏） */
function profile() {
  return request.get('/api/user/profile')
}

module.exports = { profile }
