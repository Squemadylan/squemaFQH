# CLAUDE.md

本文件是 Claude Code / 代码代理入口。仓库事实和通用约束以 `AGENTS.md` 及其索引的规则文件为准；如果本文件、历史会话或其他旧文档与当前源码冲突，先按真实文件和 `AGENTS.md` 执行。

## 读取顺序

1. 根目录 `AGENTS.md`
2. 目标目录最近的 `AGENTS.md`
3. `.claude/rules/`（自动加载，含 `.codex/rules/` 软链接）
4. 相关源码、配置、脚本
