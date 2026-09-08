#!/usr/bin/env python3
import http.server
import os

os.chdir(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

class Handler(http.server.SimpleHTTPRequestHandler):
    def guess_type(self, path):
        if path.endswith('.jad'):
            return 'text/vnd.sun.j2me.app-descriptor'
        if path.endswith('.jar'):
            return 'application/java-archive'
        return super().guess_type(path)

PORT = 8765
with http.server.HTTPServer(('0.0.0.0', PORT), Handler) as httpd:
    print(f"Serving {os.getcwd()} on 0.0.0.0:{PORT}")
    print(f"On the device, install from: http://<this-machine-ip>:{PORT}/app.jad")
    httpd.serve_forever()
