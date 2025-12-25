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
if ! command -v npm &> /dev/null; then
    echo "❌ npm 未安装，请先安装 npm"
    exit 1
fi
echo "✅ npm 版本: $(npm -v)"
echo ""

# 切换到脚本所在目录，允许在任意路径执行
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

# 检查是否在正确的目录
if [ ! -f "package.json" ]; then
    echo "❌ 未找到 package.json，请确认脚本位于 exchange-web 目录"
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

CHOKIDAR_USEPOLLING=1 CHOKIDAR_INTERVAL=100 npm run dev
