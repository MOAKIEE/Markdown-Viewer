# Markdown Viewer

一款面向 Android 的 Markdown 阅读器，支持本地文件浏览、最近文件快速打开和沉浸式阅读界面。

## 特性

- 支持从系统文件选择器打开 Markdown 文档
- 支持按目录浏览本地存储中的 `.md` 和 `.markdown` 文件
- 自动记录最近打开的文件，方便快速回到上次阅读位置
- 渲染 Markdown 内容，支持标题、列表、粗体、斜体、删除线、链接、图片、表格和代码块
- 内置目录大纲与全文搜索，适合长文阅读
- 适配浅色和深色主题

## 技术栈

- Java
- Android SDK 24+
- [Markwon](https://github.com/noties/Markwon)
- [BlurView](https://github.com/Dimezis/BlurView)
- Material 3

## 运行要求

- Android 7.0 及以上
- 需要文件访问权限，用于浏览本地 Markdown 文件

## 使用方式

1. 启动应用后，选择打开文件或浏览目录
2. 授予存储权限
3. 点击 Markdown 文件即可预览
4. 在阅读页使用顶部目录和搜索功能快速定位内容

## 项目结构

- `app/src/main/java`：主要业务代码
- `app/src/main/res/layout`：页面布局
- `app/src/main/res/drawable`：图标和背景资源
- `app/src/main/res/values`：主题、颜色和字符串资源

