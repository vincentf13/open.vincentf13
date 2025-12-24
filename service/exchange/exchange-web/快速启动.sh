#!/bin/bash

echo "================================"
echo "  Exchange Web 快速启动脚本"
echo "================================"
echo ""

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo "❌ Node.js 未安装，请先安装 Node.js"
    exit 1
fi

echo "✅ Node.js 版本: $(node -v)"
echo "✅ npm 版本: $(npm -v)"
echo ""

# 检查是否在正确的目录
if [ ! -f "package.json" ]; then
    echo "❌ 请在 web/exchange-web 目录下执行此脚本"
    exit 1
fi

# 安装依赖（如果需要）
if [ ! -d "node_modules" ]; then
    echo "📦 安装依赖..."
    npm install
    echo ""
fi

# 启动开发服务器
echo "🚀 启动开发服务器..."
echo "访问地址: http://localhost:5173"
echo ""
echo "提示："
echo "  - 按 Ctrl+C 停止服务器"
echo "  - 确保后端 Gateway 在 http://localhost:12345 运行"
echo ""

npm run dev
