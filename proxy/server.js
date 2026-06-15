const http = require('http');
const httpProxy = require('http-proxy');

// 配置：路径前缀 -> 目标服务
// 环境变量格式：PROXY_ROUTES=/user/tool/ittool->http://it-tools:80,/api/foo->http://foo:8080
const ROUTES_ENV = process.env.PROXY_ROUTES || '/user/tool/ittool->http://localhost:5050';

const routes = ROUTES_ENV.split(',').map(r => {
  const [prefix, target] = r.split('->').map(s => s.trim());
  return { prefix, target };
}).filter(r => r.prefix && r.target);

const proxy = httpProxy.createProxyServer({ changeOrigin: true });

proxy.on('error', (err, req, res) => {
  console.error(`[proxy] error for ${req.url}: ${err.message}`);
  res.writeHead(502, { 'Content-Type': 'text/plain' });
  res.end('Proxy Error');
});

const server = http.createServer((req, res) => {
  for (const route of routes) {
    if (req.url.startsWith(route.prefix)) {
      const targetPath = req.url.slice(route.prefix.length) || '/';
      req.url = targetPath;
      console.log(`[proxy] ${route.prefix}${targetPath} -> ${route.target}${targetPath}`);
      return proxy.web(req, res, { target: route.target });
    }
  }
  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not Found');
});

const PORT = process.env.PROXY_PORT || 8080;
server.listen(PORT, () => {
  console.log(`[proxy] listening on :${PORT}`);
  routes.forEach(r => console.log(`[proxy] ${r.prefix} -> ${r.target}`));
});
