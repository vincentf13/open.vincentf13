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

# 检查 npm
if ! command -v npm &> /dev/null; then
    echo "❌ npm 未安装，请先安装 npm"
    exit 1
fi
echo "✅ npm 版本: $(npm -v)"
echo ""

# 切换到脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

# 检查 package.json
if [ ! -f "package.json" ]; then
    echo "❌ 未找到 package.json，请确认脚本位于 exchange-web 目录"
    exit 1
fi

# 安装依赖
if [ ! -d "node_modules" ]; then
    echo "📦 安装依赖..."
    npm install
    echo ""
fi

# --- 互動式輸入區域 ---
DEFAULT_URL="http://localhost:12345"

# 檢查是否已有傳入參數（保持相容性）
TARGET_URL=""
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -d|--domain) TARGET_URL="$2"; shift ;;
    esac
    shift
done

# 如果沒有參數，則詢問用戶
if [ -z "$TARGET_URL" ]; then
    echo "⚙️  設定後端 API 地址"
    echo "   預設值: $DEFAULT_URL"
    read -p "   請輸入域名 (直接按 Enter 使用預設值): " INPUT_URL

    if [ -n "$INPUT_URL" ]; then
        TARGET_URL="$INPUT_URL"
    else
        TARGET_URL="$DEFAULT_URL"
    fi
fi
# --------------------

echo ""
echo "🚀 启动开发服务器..."
echo "🔗 连接后端: $TARGET_URL"
echo "👉 访问地址: http://localhost:5173"
echo ""

# 啟動 Vite
VITE_API_BASE_URL="$TARGET_URL" CHOKIDAR_USEPOLLING=1 CHOKIDAR_INTERVAL=100 npm run dev
