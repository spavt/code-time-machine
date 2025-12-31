/* eslint-env node */
module.exports = {
    root: true,
    extends: [
        'plugin:vue/vue3-recommended',
        'eslint:recommended',
        '@vue/eslint-config-typescript'
    ],
    parserOptions: {
        ecmaVersion: 'latest'
    },
    rules: {
        // 禁止 console.log，允许 console.warn 和 console.error
        'no-console': ['warn', { allow: ['warn', 'error'] }],

        // Vue 组件命名规则
        'vue/multi-word-component-names': 'off',

        // TypeScript 相关
        '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],

        // 代码风格
        'semi': ['error', 'never'],
        'quotes': ['error', 'single', { avoidEscape: true }],
        'comma-dangle': ['error', 'never'],

        // Vue 模板规则
        'vue/html-self-closing': ['error', {
            html: { void: 'always', normal: 'never', component: 'always' }
        }]
    }
}
