/** 收货地址接口 */
const request = require('../utils/request')

function list() {
  return request.get('/api/user/addresses')
}

function create(payload) {
  return request.post('/api/user/addresses', payload)
}

function update(id, payload) {
  return request.put('/api/user/addresses/' + id, payload)
}

function remove(id) {
  return request.del('/api/user/addresses/' + id)
}

module.exports = { list, create, update, remove }
