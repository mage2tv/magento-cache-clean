const babylon = require('babylon')
const traverse = require('babel-traverse').default

module.exports = function (src, {
  dynamicImport = true,
  parse = {sourceType: 'module', plugins: '*'}
} = {}) {
  const modules = {strings: [], expressions: []}

  let ast

  if (typeof src === 'string') {
    const moduleRe = /\b(require|import)\b/

    if (!moduleRe.test(src)) {
      return modules
    }

    ast = babylon.parse(src, parse)
  } else {
    ast = src
  }

  traverse(ast, {
    enter(path) {
      if (path.node.type === 'CallExpression') {
        const callee = path.get('callee')
        const isDynamicImport = dynamicImport && callee.isImport()
        if (callee.isIdentifier({name: 'require'}) || isDynamicImport) {
          const arg = path.node.arguments[0]
          if (arg.type === 'StringLiteral') {
            modules.strings.push(arg.value)
          } else {
            modules.expressions.push(src.slice(arg.start, arg.end))
          }
        }
      } else if (path.node.type === 'ImportDeclaration') {
        modules.strings.push(path.node.source.value)
      }
    }
  })

  return modules
}
