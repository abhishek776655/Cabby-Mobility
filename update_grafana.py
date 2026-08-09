import json

file_path = 'docker/grafana/dashboards/business-metrics.json'
with open(file_path, 'r') as f:
    data = json.load(f)

# Find max x and y to place the new panel at the end of the top row or a new row
panels = data.get('panels', [])

new_panel = {
    "title": "Active WebSocket Connections",
    "type": "stat",
    "gridPos": {
        "x": 12,
        "y": 4,
        "w": 4,
        "h": 4
    },
    "targets": [
        {
            "expr": "websocket_connections_active",
            "refId": "A"
        }
    ],
    "options": {
        "colorMode": "value",
        "graphMode": "area",
        "justifyMode": "auto"
    }
}

# The previous row at y=4 has panels at x=0, 4, 8. So x=12 is free.
panels.append(new_panel)
data['panels'] = panels

with open(file_path, 'w') as f:
    json.dump(data, f, indent=2)

print("Updated Grafana dashboard JSON.")
