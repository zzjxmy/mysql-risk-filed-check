#!/bin/bash

# MySQL 字段容量风险检查平台 - 快速启动脚本

set -e

echo "========================================"
echo "MySQL 字段容量风险检查平台"
echo "========================================"

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    echo "错误: 未检测到 Docker，请先安装 Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "错误: 未检测到 docker-compose，请先安装"
    exit 1
fi

# 检查环境变量文件
if [ ! -f .env ]; then
    echo "创建环境配置文件..."
    cp .env.example .env
    echo "已创建 .env 文件，请根据需要修改配置"
fi

# 启动参数
ACTION=${1:-"up"}

case $ACTION in
    "up"|"start")
        echo "启动所有服务..."
        docker-compose up -d --build
        echo ""
        echo "服务启动完成！"
        echo "- 前端地址: http://localhost"
        echo "- 后端地址: http://localhost:8080"
        echo "- 默认账号: admin / admin123"
        ;;
    "down"|"stop")
        echo "停止所有服务..."
        docker-compose down
        echo "服务已停止"
        ;;
    "restart")
        echo "重启所有服务..."
        docker-compose restart
        echo "服务已重启"
        ;;
    "logs")
        docker-compose logs -f
        ;;
    "status")
        docker-compose ps
        ;;
    "clean")
        echo "清理所有数据（包括数据库）..."
        read -p "确定要删除所有数据吗？[y/N] " confirm
        if [ "$confirm" == "y" ] || [ "$confirm" == "Y" ]; then
            docker-compose down -v
            echo "已清理所有数据"
        else
            echo "已取消"
        fi
        ;;
    *)
        echo "使用方法: $0 [命令]"
        echo ""
        echo "可用命令:"
        echo "  up|start    启动所有服务"
        echo "  down|stop   停止所有服务"
        echo "  restart     重启所有服务"
        echo "  logs        查看日志"
        echo "  status      查看服务状态"
        echo "  clean       清理所有数据"
        ;;
esac
