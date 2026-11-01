# 110 告警系统前端镜像：Vite 产物由本机构建后随上下文带入，基座取集群私有仓库 nginx
FROM registry.galaxy.local:30500/base/nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/
EXPOSE 80
